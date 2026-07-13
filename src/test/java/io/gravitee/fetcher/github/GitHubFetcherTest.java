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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.gravitee.fetcher.api.FetcherException;
import io.gravitee.fetcher.api.ResourceNotFoundException;
import io.vertx.core.Vertx;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author GraviteeSource Team
 */
class GitHubFetcherTest {

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
    void should_throw_resource_not_found_without_wrapping_when_response_is_404() {
        wiremock.stubFor(get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1")).willReturn(aResponse().withStatus(404)));

        GitHubFetcher fetcher = fetcher(10_000);

        assertThatThrownBy(fetcher::fetch)
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Unable to fetch")
            .hasNoCause();
    }

    @Test
    void should_expose_original_cause_instead_of_async_wrapper_when_fetch_fails() {
        wiremock.stubFor(get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1")).willReturn(aResponse().withStatus(500)));

        GitHubFetcher fetcher = fetcher(10_000);

        assertThatThrownBy(fetcher::fetch)
            .isInstanceOf(FetcherException.class)
            .hasCauseInstanceOf(FetcherException.class)
            .cause()
            .isNotInstanceOf(CompletionException.class);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void should_fail_fast_when_connection_is_closed_while_reading_response() {
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1")).willReturn(
                aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE)
            )
        );

        GitHubFetcher fetcher = fetcher(10_000);

        assertThatThrownBy(fetcher::fetch).isInstanceOf(FetcherException.class);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void should_fail_when_connection_stalls_while_reading_body() {
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/contents/path/to/file?ref=sha1")).willReturn(
                aResponse().withStatus(200).withBody("{\"key\": \"value\"}").withChunkedDribbleDelay(20, 20_000)
            )
        );

        GitHubFetcher fetcher = fetcher(500);

        assertThatThrownBy(fetcher::fetch).isInstanceOf(FetcherException.class);
    }

    private GitHubFetcher fetcher(int timeoutMs) {
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl(wiremock.baseUrl());
        config.setBranchOrTag("sha1");
        GitHubFetcher fetcher = new GitHubFetcher(config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", timeoutMs);
        fetcher.setVertx(vertx);
        return fetcher;
    }
}
