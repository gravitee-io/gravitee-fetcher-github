/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.fetcher.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.gravitee.fetcher.api.FetcherException;
import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com) 
 * @author GraviteeSource Team
 */
public class GitHubFetcher_FetchTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    private GitHubFetcher fetcher = new GitHubFetcher(null);

    private Vertx vertx = Vertx.vertx();
    private ObjectMapper mapper = new ObjectMapper();

    @Before
    public void init() {
        ReflectionTestUtils.setField(fetcher, "vertx", vertx);
        ReflectionTestUtils.setField(fetcher, "mapper", mapper);
    }

    @Test
    public void shouldNotFetchWithoutContent() throws FetcherException {
        stubFor(get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"key\": \"value\"}")));
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl("http://localhost:" + wireMockRule.port());
        config.setBranchOrTag("sha1");
        ReflectionTestUtils.setField(fetcher, "gitHubFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);

        InputStream fetch = fetcher.fetch().getContent();

        assertThat(fetch).isNull();
    }

    @Test
    public void shouldNotFetchEmptyBody() throws Exception {
        stubFor(get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1"))
                .willReturn(aResponse()
                        .withStatus(200)));
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl("http://localhost:" + wireMockRule.port());
        config.setBranchOrTag("sha1");
        ReflectionTestUtils.setField(fetcher, "gitHubFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);

        InputStream fetch = fetcher.fetch().getContent();
        assertThat(fetch).isNull();
    }

    @Test(expected = Exception.class)
    public void shouldThrowExceptionIfContentNotBase64() throws Exception {
        stubFor(get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"content\": \"not base64 content\"}")));
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl("http://localhost:" + wireMockRule.port());
        config.setBranchOrTag("sha1");
        ReflectionTestUtils.setField(fetcher, "gitHubFetcherConfiguration", config);

        fetcher.fetch();

        fail("Decode non base64 content does not throw Exception");
    }

    @Test
    public void shouldFetchBase64Content() throws Exception {
        String content = "Gravitee.io is awesome!";
        String encoded = Base64.getEncoder().encodeToString(content.getBytes());

        stubFor(get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"content\": \""+encoded+"\"}")));
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl("http://localhost:" + wireMockRule.port());
        config.setBranchOrTag("sha1");
        ReflectionTestUtils.setField(fetcher, "gitHubFetcherConfiguration", config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);

        InputStream fetch = fetcher.fetch().getContent();

        assertThat(fetch).isNotNull();
        int n = fetch.available();
        byte[] bytes = new byte[n];
        fetch.read(bytes, 0, n);
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo(content);
    }

    @Test(expected = FetcherException.class)
    public void shouldThrowExceptionWhenStatusNot200() throws Exception {
        stubFor(get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("{\n" +
                                "  \"message\": \"401 Unauthorized\"\n" +
                                "}")));
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl("http://localhost:" + wireMockRule.port());
        config.setBranchOrTag("sha1");
        ReflectionTestUtils.setField(fetcher, "gitHubFetcherConfiguration", config);

        try {
            fetcher.fetch();
        } catch (FetcherException fe) {
            assertThat(fe.getMessage().contains("Status code: 401"));
            assertThat(fe.getMessage().contains("Message: 401 Unauthorized"));
            throw fe;
        }

        fail("Fetch response with status code != 200 does not throw Exception");
    }
}
