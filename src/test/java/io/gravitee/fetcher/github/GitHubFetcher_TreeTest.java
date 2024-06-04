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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.gravitee.fetcher.api.FetcherException;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
class GitHubFetcher_TreeTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Test
    public void shouldNotTreeWithoutContent() throws FetcherException {
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/git/trees/sha1?recursive=1"))
                .willReturn(aResponse().withStatus(200).withBody("{\"truncated\": \"false\"}"))
        );
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl("http://localhost:" + wiremock.getPort());
        config.setBranchOrTag("sha1");
        GitHubFetcher fetcher = new GitHubFetcher(config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);
        fetcher.setVertx(Vertx.vertx());

        String[] tree = fetcher.files();

        assertThat(tree).isNullOrEmpty();
    }

    @Test
    public void shouldNotTreeEmptyBody() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/repos/owner/myrepo/git/trees/sha1?recursive=1")).willReturn(aResponse().withStatus(200)));
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl("http://localhost:" + wiremock.getPort());
        config.setBranchOrTag("sha1");
        GitHubFetcher fetcher = new GitHubFetcher(config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);
        fetcher.setVertx(Vertx.vertx());

        String[] tree = fetcher.files();

        assertThat(tree).isNullOrEmpty();
    }

    @Test
    public void shouldTree() throws Exception {
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/git/trees/sha1?recursive=1")).willReturn(aResponse().withStatus(200).withBody(treeResponse))
        );
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl("http://localhost:" + wiremock.getPort());
        config.setBranchOrTag("sha1");
        GitHubFetcher fetcher = new GitHubFetcher(config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);
        fetcher.setVertx(Vertx.vertx());

        String[] tree = fetcher.files();

        assertThat(tree).isNotEmpty();
        assertThat(tree).hasSize(5);
        assertThat(tree)
            .contains(
                "/path/to/file/swagger.yml",
                "/path/to/file/doc.md",
                "/path/to/file/doc2.MD",
                "/path/to/file/doc2.UNKNOWN",
                "/path/to/file/subpath/doc.md"
            );
    }

    @Test
    public void shouldTreeWithEmptyPath() throws Exception {
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/git/trees/sha1?recursive=1")).willReturn(aResponse().withStatus(200).withBody(treeResponse))
        );
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath(null);
        config.setGithubUrl("http://localhost:" + wiremock.getPort());
        config.setBranchOrTag("sha1");
        GitHubFetcher fetcher = new GitHubFetcher(config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 10_000);
        fetcher.setVertx(Vertx.vertx());

        String[] tree = fetcher.files();

        assertThat(tree).isNotEmpty();
        assertThat(tree).hasSize(7);
        assertThat(tree)
            .contains(
                "/path/to/file/swagger.yml",
                "/path/to/file/doc.md",
                "/path/to/file/doc2.MD",
                "/path/to/file/doc2.UNKNOWN",
                "/path/to/file/subpath/doc.md",
                "/CONTRIBUTING.md",
                "/path/not/to/file/doc.md"
            );
    }

    @Test
    public void shouldThrowExceptionWhenStatusNot200() throws Exception {
        wiremock.stubFor(
            get(urlEqualTo("/repos/owner/myrepo/git/trees/sha1?recursive=1"))
                .willReturn(aResponse().withStatus(401).withBody("{\n" + "  \"message\": \"401 Unauthorized\"\n" + "}"))
        );
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl("http://localhost:" + wiremock.getPort());
        config.setBranchOrTag("sha1");
        GitHubFetcher fetcher = new GitHubFetcher(config);
        fetcher.setVertx(Vertx.vertx());

        assertThatThrownBy(fetcher::files).isInstanceOf(FetcherException.class).hasMessageContaining("Unable to fetch GitHub content (");
    }

    private final String treeResponse =
        """
                    {
                        "sha": "28bd3de3c32304841eb69d80079ffcc447f9ce6f",
                        "url": "https://api.github.com/repos/owner/myrepo/git/trees/28bd3de3c32304841eb69d80079ffcc447f9ce6f",
                        "tree": [
                            {
                                "path": ".gitignore",
                                "mode": "100644",
                                "type": "blob",
                                "sha": "a05ec7b1a209b7bc47575bf2fea9b2b4396c0bc4",
                                "size": 480,
                                "url": "https://api.github.com/repos/owner/myrepo/git/blobs/a05ec7b1a209b7bc47575bf2fea9b2b4396c0bc4"
                            },
                            {
                                "path": "CONTRIBUTING.md",
                                "mode": "100644",
                                "type": "blob",
                                "sha": "ae2062778d56d2cf8a52bd5047555ecba3e6b6df",
                                "size": 3351,
                                "url": "https://api.github.com/repos/owner/myrepo/git/blobs/ae2062778d56d2cf8a52bd5047555ecba3e6b6df"
                            },
                            {
                                "path": "path/to/file/swagger.yml",
                                "mode": "100644",
                                "type": "blob",
                                "sha": "05569e2afcbcb85f461e311b332f4e30cff21a6a",
                                "size": 12,
                                "url": "https://api.github.com/repos/owner/myrepo/git/blobs/05569e2afcbcb85f461e311b332f4e30cff21a6a"
                            },
                            {
                                "path": "path/to/file/doc.md",
                                "mode": "100644",
                                "type": "blob",
                                "sha": "8f71f43fee3f78649d238238cbde51e6d7055c82",
                                "size": 11358,
                                "url": "https://api.github.com/repos/owner/myrepo/git/blobs/8f71f43fee3f78649d238238cbde51e6d7055c82"
                            },
                            {
                                "path": "path/to/file/",
                                "mode": "100644",
                                "type": "tree",
                                "sha": "8f71f43fee3f78649d238238cbde51e6d7055c82",
                                "size": 11358,
                                "url": "https://api.github.com/repos/owner/myrepo/git/blobs/8f71f43fee3f78649d238238cbde51e6d7055c82"
                            },
                            {
                                "path": "path/to/file/doc2.MD",
                                "mode": "100644",
                                "type": "blob",
                                "sha": "8f71f43fee3f78649d238238cbde51e6d7055c82",
                                "size": 11358,
                                "url": "https://api.github.com/repos/owner/myrepo/git/blobs/8f71f43fee3f78649d238238cbde51e6d7055c82"
                            },
                            {
                                "path": "path/to/file/doc2.UNKNOWN",
                                "mode": "100644",
                                "type": "blob",
                                "sha": "8f71f43fee3f78649d238238cbde51e6d7055c82",
                                "size": 11358,
                                "url": "https://api.github.com/repos/owner/myrepo/git/blobs/8f71f43fee3f78649d238238cbde51e6d7055c82"
                            },
                            {
                                "path": "path/not/to/file/doc.md",
                                "mode": "100644",
                                "type": "blob",
                                "sha": "8f71f43fee3f78649d238238cbde51e6d7055c82",
                                "size": 11358,
                                "url": "https://api.github.com/repos/owner/myrepo/git/blobs/8f71f43fee3f78649d238238cbde51e6d7055c82"
                            },
                            {
                                "path": "path/to/file/subpath/",
                                "mode": "100644",
                                "type": "tree",
                                "sha": "e3958323e580277dc7394fa5b148afbb053e0105",
                                "size": 1208,
                                "url": "https://api.github.com/repos/owner/myrepo/git/blobs/e3958323e580277dc7394fa5b148afbb053e0105"
                            },
                            {
                                "path": "path/to/file/subpath/doc.md",
                                "mode": "100644",
                                "type": "blob",
                                "sha": "e3958323e580277dc7394fa5b148afbb053e0105",
                                "size": 1208,
                                "url": "https://api.github.com/repos/owner/myrepo/git/blobs/e3958323e580277dc7394fa5b148afbb053e0105"
                            }
                        ],
                        "truncated": false\
                    }""";
}
