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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.gravitee.fetcher.api.FetcherException;
import io.vertx.core.Vertx;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GitHubFetcher_TreeTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    @Test
    public void shouldNotTreeWithoutContent() throws FetcherException {
        stubFor(get(urlEqualTo("/repos/owner/myrepo/git/trees/sha1?recursive=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"truncated\": \"false\"}")));
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl("http://localhost:" + wireMockRule.port());
        config.setBranchOrTag("sha1");
        GitHubFetcher fetcher = new GitHubFetcher(config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);
        fetcher.setVertx(Vertx.vertx());

        String[] tree = fetcher.files();

        assertThat(tree).isNullOrEmpty();
    }

    @Test
    public void shouldNotTreeEmptyBody() throws Exception {
        stubFor(get(urlEqualTo("/repos/owner/myrepo/git/trees/sha1?recursive=1"))
                .willReturn(aResponse()
                        .withStatus(200)));
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl("http://localhost:" + wireMockRule.port());
        config.setBranchOrTag("sha1");
        GitHubFetcher fetcher = new GitHubFetcher(config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);
        fetcher.setVertx(Vertx.vertx());

        String[] tree = fetcher.files();

        assertThat(tree).isNullOrEmpty();
    }

    @Test
    public void shouldTree() throws Exception {
        stubFor(get(urlEqualTo("/repos/owner/myrepo/git/trees/sha1?recursive=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(treeResponse)));
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath("/path/to/file");
        config.setGithubUrl("http://localhost:" + wireMockRule.port());
        config.setBranchOrTag("sha1");
        GitHubFetcher fetcher = new GitHubFetcher(config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);
        fetcher.setVertx(Vertx.vertx());

        String[] tree = fetcher.files();

        assertThat(tree).isNotEmpty();
        assertEquals("get 5 files", 5, tree.length);
        List<String> asList = Arrays.asList(tree);
        assertTrue("swagger.yml", asList.contains("/path/to/file/swagger.yml"));
        assertTrue("doc.md", asList.contains("/path/to/file/doc.md"));
        assertTrue("doc2.MD", asList.contains("/path/to/file/doc2.MD"));
        assertTrue("doc2.MD", asList.contains("/path/to/file/doc2.UNKNOWN"));
        assertTrue("subpath/doc.md", asList.contains("/path/to/file/subpath/doc.md"));
    }

    @Test
    public void shouldTreeWithEmptyPath() throws Exception {
        stubFor(get(urlEqualTo("/repos/owner/myrepo/git/trees/sha1?recursive=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(treeResponse)));
        GitHubFetcherConfiguration config = new GitHubFetcherConfiguration();
        config.setOwner("owner");
        config.setRepository("myrepo");
        config.setFilepath(null);
        config.setGithubUrl("http://localhost:" + wireMockRule.port());
        config.setBranchOrTag("sha1");
        GitHubFetcher fetcher = new GitHubFetcher(config);
        ReflectionTestUtils.setField(fetcher, "httpClientTimeout", 1_000);
        fetcher.setVertx(Vertx.vertx());

        String[] tree = fetcher.files();

        assertThat(tree).isNotEmpty();
        assertEquals("get 7 files", 7, tree.length);
        List<String> asList = Arrays.asList(tree);
        assertTrue("swagger.yml", asList.contains("/path/to/file/swagger.yml"));
        assertTrue("doc.md", asList.contains("/path/to/file/doc.md"));
        assertTrue("doc2.MD", asList.contains("/path/to/file/doc2.MD"));
        assertTrue("doc2.MD", asList.contains("/path/to/file/doc2.UNKNOWN"));
        assertTrue("subpath/doc.md", asList.contains("/path/to/file/subpath/doc.md"));
        assertTrue("CONTRIBUTING.md", asList.contains("/CONTRIBUTING.md"));
        assertTrue("path/not/to/file/doc.md", asList.contains("/path/not/to/file/doc.md"));

    }

    @Test(expected = FetcherException.class)
    public void shouldThrowExceptionWhenStatusNot200() throws Exception {
        stubFor(get(urlEqualTo("/repos/owner/myrepo/git/trees/sha1?recursive=1"))
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
        GitHubFetcher fetcher = new GitHubFetcher(config);
        fetcher.setVertx(Vertx.vertx());

        try {
            fetcher.files();
        } catch (FetcherException fe) {
            assertThat(fe.getMessage().contains("Unable to fetch GitHub content (")).isTrue();
            throw fe;
        }

        fail("Fetch response with status code != 200 does not throw Exception");
    }

    private String treeResponse =
            "{\n" +
                    "    \"sha\": \"28bd3de3c32304841eb69d80079ffcc447f9ce6f\",\n" +
                    "    \"url\": \"https://api.github.com/repos/owner/myrepo/git/trees/28bd3de3c32304841eb69d80079ffcc447f9ce6f\",\n" +
                    "    \"tree\": [\n" +
                    "        {\n" +
                    "            \"path\": \".gitignore\",\n" +
                    "            \"mode\": \"100644\",\n" +
                    "            \"type\": \"blob\",\n" +
                    "            \"sha\": \"a05ec7b1a209b7bc47575bf2fea9b2b4396c0bc4\",\n" +
                    "            \"size\": 480,\n" +
                    "            \"url\": \"https://api.github.com/repos/owner/myrepo/git/blobs/a05ec7b1a209b7bc47575bf2fea9b2b4396c0bc4\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"path\": \"CONTRIBUTING.md\",\n" +
                    "            \"mode\": \"100644\",\n" +
                    "            \"type\": \"blob\",\n" +
                    "            \"sha\": \"ae2062778d56d2cf8a52bd5047555ecba3e6b6df\",\n" +
                    "            \"size\": 3351,\n" +
                    "            \"url\": \"https://api.github.com/repos/owner/myrepo/git/blobs/ae2062778d56d2cf8a52bd5047555ecba3e6b6df\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"path\": \"path/to/file/swagger.yml\",\n" +
                    "            \"mode\": \"100644\",\n" +
                    "            \"type\": \"blob\",\n" +
                    "            \"sha\": \"05569e2afcbcb85f461e311b332f4e30cff21a6a\",\n" +
                    "            \"size\": 12,\n" +
                    "            \"url\": \"https://api.github.com/repos/owner/myrepo/git/blobs/05569e2afcbcb85f461e311b332f4e30cff21a6a\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"path\": \"path/to/file/doc.md\",\n" +
                    "            \"mode\": \"100644\",\n" +
                    "            \"type\": \"blob\",\n" +
                    "            \"sha\": \"8f71f43fee3f78649d238238cbde51e6d7055c82\",\n" +
                    "            \"size\": 11358,\n" +
                    "            \"url\": \"https://api.github.com/repos/owner/myrepo/git/blobs/8f71f43fee3f78649d238238cbde51e6d7055c82\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"path\": \"path/to/file/\",\n" +
                    "            \"mode\": \"100644\",\n" +
                    "            \"type\": \"tree\",\n" +
                    "            \"sha\": \"8f71f43fee3f78649d238238cbde51e6d7055c82\",\n" +
                    "            \"size\": 11358,\n" +
                    "            \"url\": \"https://api.github.com/repos/owner/myrepo/git/blobs/8f71f43fee3f78649d238238cbde51e6d7055c82\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"path\": \"path/to/file/doc2.MD\",\n" +
                    "            \"mode\": \"100644\",\n" +
                    "            \"type\": \"blob\",\n" +
                    "            \"sha\": \"8f71f43fee3f78649d238238cbde51e6d7055c82\",\n" +
                    "            \"size\": 11358,\n" +
                    "            \"url\": \"https://api.github.com/repos/owner/myrepo/git/blobs/8f71f43fee3f78649d238238cbde51e6d7055c82\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"path\": \"path/to/file/doc2.UNKNOWN\",\n" +
                    "            \"mode\": \"100644\",\n" +
                    "            \"type\": \"blob\",\n" +
                    "            \"sha\": \"8f71f43fee3f78649d238238cbde51e6d7055c82\",\n" +
                    "            \"size\": 11358,\n" +
                    "            \"url\": \"https://api.github.com/repos/owner/myrepo/git/blobs/8f71f43fee3f78649d238238cbde51e6d7055c82\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"path\": \"path/not/to/file/doc.md\",\n" +
                    "            \"mode\": \"100644\",\n" +
                    "            \"type\": \"blob\",\n" +
                    "            \"sha\": \"8f71f43fee3f78649d238238cbde51e6d7055c82\",\n" +
                    "            \"size\": 11358,\n" +
                    "            \"url\": \"https://api.github.com/repos/owner/myrepo/git/blobs/8f71f43fee3f78649d238238cbde51e6d7055c82\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"path\": \"path/to/file/subpath/\",\n" +
                    "            \"mode\": \"100644\",\n" +
                    "            \"type\": \"tree\",\n" +
                    "            \"sha\": \"e3958323e580277dc7394fa5b148afbb053e0105\",\n" +
                    "            \"size\": 1208,\n" +
                    "            \"url\": \"https://api.github.com/repos/owner/myrepo/git/blobs/e3958323e580277dc7394fa5b148afbb053e0105\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"path\": \"path/to/file/subpath/doc.md\",\n" +
                    "            \"mode\": \"100644\",\n" +
                    "            \"type\": \"blob\",\n" +
                    "            \"sha\": \"e3958323e580277dc7394fa5b148afbb053e0105\",\n" +
                    "            \"size\": 1208,\n" +
                    "            \"url\": \"https://api.github.com/repos/owner/myrepo/git/blobs/e3958323e580277dc7394fa5b148afbb053e0105\"\n" +
                    "        }\n" +
                    "    ],\n" +
                    "    \"truncated\": false" +
                    "}";

}
