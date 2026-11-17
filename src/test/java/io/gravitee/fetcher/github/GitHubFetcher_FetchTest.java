/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.fetcher.github;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.gravitee.fetcher.api.FetcherException;
import io.gravitee.fetcher.api.FilesFetcher;
import io.gravitee.fetcher.api.Resource;
import io.gravitee.fetcher.api.ResourceNotFoundException;
import io.vertx.core.Vertx;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
class GitHubFetcher_FetchTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private GitHubFetcher fetcher = new GitHubFetcher(null);

    private Vertx vertx = Vertx.vertx();
    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    public void init() {
        ReflectionTestUtils.setField(fetcher, "vertx", vertx);
        ReflectionTestUtils.setField(fetcher, "mapper", mapper);
    }

    @Test
    public void shouldNotFetchWithoutContent() throws FetcherException {
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1")).willReturn(
                aResponse().withStatus(200).withBody("{\"key\": \"value\"}")
            )
        );
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl(wiremock.baseUrl());
        config.setBranchOrTag("sha1");
        ReflectionTestUtils.setField(fetcher, "gitHubFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        InputStream fetch = fetcher.fetch().getContent();

        assertThat(fetch).isNull();
    }

    @Test
    public void shouldNotFetchEmptyBody() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1")).willReturn(aResponse().withStatus(200)));
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl(wiremock.baseUrl());
        config.setBranchOrTag("sha1");
        ReflectionTestUtils.setField(fetcher, "gitHubFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        InputStream fetch = fetcher.fetch().getContent();
        assertThat(fetch).isNull();
    }

    @Test
    public void shouldThrowExceptionIfContentNotBase64() throws Exception {
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1")).willReturn(
                aResponse().withStatus(200).withBody("{\"content\": \"not base64 content\"}")
            )
        );
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl(wiremock.baseUrl());
        config.setBranchOrTag("sha1");
        ReflectionTestUtils.setField(fetcher, "gitHubFetcherConfiguration", config);

        assertThatThrownBy(fetcher::fetch).hasMessage("Illegal base64 character 20");
    }

    @Test
    public void shouldFetchBase64Content() throws Exception {
        String content = "Gravitee.io is awesome!";
        String encoded = Base64.getEncoder().encodeToString(content.getBytes());

        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1")).willReturn(
                aResponse().withStatus(200).withBody("{\"content\": \"" + encoded + "\"}")
            )
        );
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl(wiremock.baseUrl());
        config.setBranchOrTag("sha1");
        ReflectionTestUtils.setField(fetcher, "gitHubFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        InputStream fetch = fetcher.fetch().getContent();

        assertThat(fetch).isNotNull();
        int n = fetch.available();
        byte[] bytes = new byte[n];
        fetch.read(bytes, 0, n);
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo(content);
    }

    @Test
    public void shouldFetchRawContentWhenFileIsTooLargeForContentsApi() throws Exception {
        String content = "A swagger larger than the 1 MB the contents API inlines";

        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1"))
                .withHeader("Accept", equalTo("application/vnd.github.v3+json"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(
                            "{\"content\": \"\", \"encoding\": \"none\", \"html_url\": \"https://github.com/owner/myrepo/blob/sha1/path/to/file\"}"
                        )
                )
        );
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1"))
                .withHeader("Accept", equalTo("application/vnd.github.v3.raw"))
                .withHeader("Authorization", equalTo("Basic " + Base64.getEncoder().encodeToString("user:token".getBytes())))
                .willReturn(aResponse().withStatus(200).withBody(content))
        );
        GitHubFetcherConfiguration config = config();
        config.setUsername("user");
        config.setPersonalAccessToken("token");
        ReflectionTestUtils.setField(fetcher, "gitHubFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        Resource resource = fetcher.fetch();

        assertThat(new String(resource.getContent().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(content);
        assertThat(resource.getMetadata())
            .doesNotContainKey("content")
            .containsEntry(FilesFetcher.EDIT_URL_PROPERTY_KEY, "https://github.com/owner/myrepo/edit/sha1/path/to/file");
    }

    @Test
    public void shouldThrowExceptionWhenRawContentCannotBeFetched() {
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1"))
                .withHeader("Accept", equalTo("application/vnd.github.v3+json"))
                .willReturn(aResponse().withStatus(200).withBody("{\"content\": \"\", \"encoding\": \"none\"}"))
        );
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1"))
                .withHeader("Accept", equalTo("application/vnd.github.v3.raw"))
                .willReturn(aResponse().withStatus(500))
        );
        ReflectionTestUtils.setField(fetcher, "gitHubFetcherConfiguration", config());
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        assertThatThrownBy(fetcher::fetch)
            .isInstanceOf(FetcherException.class)
            .hasMessageContaining("Status code: 500")
            .hasCauseInstanceOf(FetcherException.class);
    }

    @Test
    public void shouldThrowResourceNotFoundWhenRawContentIsGone() {
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1"))
                .withHeader("Accept", equalTo("application/vnd.github.v3+json"))
                .willReturn(aResponse().withStatus(200).withBody("{\"content\": \"\", \"encoding\": \"none\"}"))
        );
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1"))
                .withHeader("Accept", equalTo("application/vnd.github.v3.raw"))
                .willReturn(aResponse().withStatus(404))
        );
        ReflectionTestUtils.setField(fetcher, "gitHubFetcherConfiguration", config());
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        assertThatThrownBy(fetcher::fetch).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    public void shouldNotRequestRawContentForEmptyFile() throws Exception {
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1")).willReturn(
                aResponse().withStatus(200).withBody("{\"content\": \"\", \"encoding\": \"base64\"}")
            )
        );
        ReflectionTestUtils.setField(fetcher, "gitHubFetcherConfiguration", config());
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);

        Resource resource = fetcher.fetch();

        assertThat(resource.getContent().readAllBytes()).isEmpty();
        wiremock.verify(1, getRequestedFor(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1")));
    }

    @Test
    public void shouldThrowExceptionWhenStatusNot200() throws Exception {
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1")).willReturn(
                aResponse().withStatus(401).withBody("{\n" + "  \"message\": \"401 Unauthorized\"\n" + "}")
            )
        );
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl(wiremock.baseUrl());
        config.setBranchOrTag("sha1");
        ReflectionTestUtils.setField(fetcher, "gitHubFetcherConfiguration", config);

        assertThatThrownBy(fetcher::fetch)
            .isInstanceOf(FetcherException.class)
            .hasMessageContaining("Status code: 401")
            .hasMessageContaining("Message: Unauthorized");
    }

    private GitHubFetcherConfiguration config() {
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl(wiremock.baseUrl());
        config.setBranchOrTag("sha1");
        return config;
    }
}
