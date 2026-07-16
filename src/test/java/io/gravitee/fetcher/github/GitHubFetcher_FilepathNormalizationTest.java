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
import io.gravitee.fetcher.api.Resource;
import io.gravitee.fetcher.api.ResourceNotFoundException;
import io.vertx.core.Vertx;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.util.ReflectionTestUtils;

class GitHubFetcher_FilepathNormalizationTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private Vertx vertx;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
    }

    @AfterEach
    void tearDown() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Test
    void should_fetch_content_when_filepath_has_no_leading_slash() throws Exception {
        stubContent();

        GitHubFetcher fetcher = fetcher("path/to/file");
        Resource resource = fetcher.fetch();

        assertThat(resource).isNotNull();
        assertThat(new String(resource.getContent().readAllBytes())).isEqualTo("Gravitee.io is awesome!");
    }

    @Test
    void should_include_filepath_repository_and_ref_in_not_found_message() {
        wiremock.stubFor(get(urlEqualTo("/repos/owner/myrepo/contents/path/to/unknown?ref=sha1")).willReturn(aResponse().withStatus(404)));

        GitHubFetcher fetcher = fetcher("/path/to/unknown");

        assertThatThrownBy(fetcher::fetch)
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Unable to fetch file '/path/to/unknown'")
            .hasMessageContaining("owner/myrepo")
            .hasMessageContaining("ref: sha1");
    }

    private void stubContent() {
        String encodedContent = Base64.getEncoder().encodeToString("Gravitee.io is awesome!".getBytes());
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1")).willReturn(
                aResponse().withStatus(200).withBody("{\"content\": \"" + encodedContent + "\"}")
            )
        );
    }

    private GitHubFetcher fetcher(String filepath) {
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath(filepath);
        config.setGithubUrl(wiremock.baseUrl());
        config.setBranchOrTag("sha1");
        GitHubFetcher fetcher = new GitHubFetcher(config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);
        ReflectionTestUtils.setField(fetcher, "mapper", new ObjectMapper());
        fetcher.setVertx(vertx);
        return fetcher;
    }
}
