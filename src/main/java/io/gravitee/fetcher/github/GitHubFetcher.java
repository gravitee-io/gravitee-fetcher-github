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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.utils.UUID;
import io.gravitee.fetcher.api.*;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class GitHubFetcher implements FilesFetcher {

    private static final String HTTPS_SCHEME = "https";
    private static final String VERSION_HEADER = "application/vnd.github.v3+json";
    private GitHubFetcherConfiguration gitHubFetcherConfiguration;

    @Autowired
    private Vertx vertx;

    @Autowired
    private ObjectMapper mapper;

    @Value("${httpClient.timeout:10000}")
    private int httpClientTimeout;

    @Value("${httpClient.proxy.type:HTTP}")
    private String httpClientProxyType;

    @Value("${httpClient.proxy.http.host:#{systemProperties['http.proxyHost'] ?: 'localhost'}}")
    private String httpClientProxyHttpHost;

    @Value("${httpClient.proxy.http.port:#{systemProperties['http.proxyPort'] ?: 3128}}")
    private int httpClientProxyHttpPort;

    @Value("${httpClient.proxy.http.username:#{null}}")
    private String httpClientProxyHttpUsername;

    @Value("${httpClient.proxy.http.password:#{null}}")
    private String httpClientProxyHttpPassword;

    @Value("${httpClient.proxy.https.host:#{systemProperties['https.proxyHost'] ?: 'localhost'}}")
    private String httpClientProxyHttpsHost;

    @Value("${httpClient.proxy.https.port:#{systemProperties['https.proxyPort'] ?: 3128}}")
    private int httpClientProxyHttpsPort;

    @Value("${httpClient.proxy.https.username:#{null}}")
    private String httpClientProxyHttpsUsername;

    @Value("${httpClient.proxy.https.password:#{null}}")
    private String httpClientProxyHttpsPassword;

    public GitHubFetcher(GitHubFetcherConfiguration cfg) {
        this.gitHubFetcherConfiguration = cfg;
    }

    @Override
    public FetcherConfiguration getConfiguration() {
        return this.gitHubFetcherConfiguration;
    }

    @Override
    public Resource fetch() throws FetcherException {
        checkRequiredFields(true);
        JsonNode jsonNode = this.request(getFetchUrl());

        final Resource resource = new Resource();
        if (jsonNode != null) {
            final Map<String, Object> metadata = mapper.convertValue(jsonNode, Map.class);
            final Object content = metadata.remove("content");
            if (content != null) {
                final String contentAsBase64 = String.valueOf(content).replaceAll("\\n", "");
                byte[] decodedContent = Base64.getDecoder().decode(contentAsBase64);
                resource.setContent(new ByteArrayInputStream(decodedContent));
            }
            final Object htmlUrl = metadata.get("html_url");
            if (htmlUrl != null) {
                metadata.put(EDIT_URL_PROPERTY_KEY, String.valueOf(htmlUrl).replace("blob", "edit"));
            }
            metadata.put(PROVIDER_NAME_PROPERTY_KEY, "GitHub");
            resource.setMetadata(metadata);
        }
        return resource;
    }

    @Override
    public String[] files() throws FetcherException {
        checkRequiredFields(false);
        if ((gitHubFetcherConfiguration.getFilepath() == null || gitHubFetcherConfiguration.getFilepath().isEmpty())) {
            gitHubFetcherConfiguration.setFilepath("/");
        }
        JsonNode jsonNode = this.request(getTreeUrl());
        List<String> result = new ArrayList<>();
        if (jsonNode != null) {
            JsonNode truncated = jsonNode.get("truncated");
            if (truncated != null && !truncated.asBoolean(false)) {
                JsonNode rawTree = jsonNode.get("tree");
                if (rawTree != null && rawTree.isArray()) {
                    String filepath = gitHubFetcherConfiguration.getFilepath().trim();
                    if (filepath.startsWith("/")) {
                        filepath = filepath.replaceFirst("/", "");
                    }
                    ArrayNode tree = (ArrayNode) rawTree;
                    Iterator<JsonNode> elements = tree.elements();
                    while (elements.hasNext()) {
                        JsonNode elt = elements.next();
                        String type = elt.get("type").asText();
                        String path = elt.get("path").asText();
                        if ("blob".equals(type) && (filepath.isEmpty() || path.startsWith(filepath))) {
                            int lastIndexOfDot = path.lastIndexOf('.');
                            if (lastIndexOfDot > 0) {
                                result.add("/" + path);
                            }
                        }
                    }
                }
            } else {
                throw new FetcherException("Too many tree elements to retrieve.", null);
            }
        }
        return result.toArray(new String[0]);
    }

    private void checkRequiredFields(boolean checkFilepath) throws FetcherException {
        if (
            gitHubFetcherConfiguration.getGithubUrl() == null ||
            gitHubFetcherConfiguration.getGithubUrl().isEmpty() ||
            gitHubFetcherConfiguration.getOwner() == null ||
            gitHubFetcherConfiguration.getOwner().isEmpty() ||
            gitHubFetcherConfiguration.getRepository() == null ||
            gitHubFetcherConfiguration.getRepository().isEmpty() ||
            (gitHubFetcherConfiguration.isAutoFetch() &&
                (gitHubFetcherConfiguration.getFetchCron() == null || gitHubFetcherConfiguration.getFetchCron().isEmpty())) ||
            (checkFilepath && (gitHubFetcherConfiguration.getFilepath() == null || gitHubFetcherConfiguration.getFilepath().isEmpty()))
        ) {
            throw new FetcherException("Some required configuration attributes are missing.", null);
        }

        if (gitHubFetcherConfiguration.isAutoFetch()) {
            try {
                CronExpression.parse(gitHubFetcherConfiguration.getFetchCron());
            } catch (IllegalArgumentException e) {
                throw new FetcherException("Cron expression is invalid", e);
            }
        }
    }

    private String getFetchUrl() {
        return (
            gitHubFetcherConfiguration.getGithubUrl() +
            "/repos" +
            "/" +
            gitHubFetcherConfiguration.getOwner() +
            "/" +
            gitHubFetcherConfiguration.getRepository() +
            "/contents" +
            gitHubFetcherConfiguration.getFilepath() +
            (gitHubFetcherConfiguration.getBranchOrTag() != null && !gitHubFetcherConfiguration.getBranchOrTag().isEmpty()
                    ? ("?ref=" + gitHubFetcherConfiguration.getBranchOrTag())
                    : "")
        );
    }

    private String getTreeUrl() {
        return (
            gitHubFetcherConfiguration.getGithubUrl() +
            "/repos" +
            "/" +
            gitHubFetcherConfiguration.getOwner() +
            "/" +
            gitHubFetcherConfiguration.getRepository() +
            "/git/trees/" +
            (gitHubFetcherConfiguration.getBranchOrTag() != null && !gitHubFetcherConfiguration.getBranchOrTag().isEmpty()
                    ? (gitHubFetcherConfiguration.getBranchOrTag())
                    : "master") +
            "?recursive=1"
        );
    }

    private JsonNode request(String url) throws FetcherException {
        try {
            Buffer buffer = fetchContent(url).join();
            if (buffer == null || buffer.length() == 0) {
                log.warn("Something goes wrong, GitHub responds with a status 200 but the content is empty.");
                return null;
            }

            return new ObjectMapper().readTree(buffer.getBytes());
        } catch (Exception ex) {
            Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof ResourceNotFoundException resourceNotFoundException) {
                throw resourceNotFoundException;
            }

            log.error(cause.getMessage(), cause);
            throw new FetcherException("Unable to fetch GitHub content (" + cause.getMessage() + ")", cause);
        }
    }

    private CompletableFuture<Buffer> fetchContent(String url) throws Exception {
        Promise<Buffer> promise = Promise.promise();

        URI requestUri = URI.create(url);
        boolean ssl = HTTPS_SCHEME.equalsIgnoreCase(requestUri.getScheme());

        final HttpClientOptions options = new HttpClientOptions()
            .setSsl(ssl)
            .setTrustAll(true)
            .setKeepAlive(false)
            .setTcpKeepAlive(false)
            .setConnectTimeout(httpClientTimeout)
            .setIdleTimeout(httpClientTimeout)
            .setIdleTimeoutUnit(TimeUnit.MILLISECONDS);

        final PoolOptions poolOptions = new PoolOptions().setHttp1MaxSize(1);

        if (gitHubFetcherConfiguration.isUseSystemProxy()) {
            ProxyOptions proxyOptions = new ProxyOptions();
            proxyOptions.setType(ProxyType.valueOf(httpClientProxyType));
            if (HTTPS_SCHEME.equals(requestUri.getScheme())) {
                proxyOptions.setHost(httpClientProxyHttpsHost);
                proxyOptions.setPort(httpClientProxyHttpsPort);
                proxyOptions.setUsername(httpClientProxyHttpsUsername);
                proxyOptions.setPassword(httpClientProxyHttpsPassword);
            } else {
                proxyOptions.setHost(httpClientProxyHttpHost);
                proxyOptions.setPort(httpClientProxyHttpPort);
                proxyOptions.setUsername(httpClientProxyHttpUsername);
                proxyOptions.setPassword(httpClientProxyHttpPassword);
            }
            options.setProxyOptions(proxyOptions);
        }

        final HttpClient httpClient = vertx.createHttpClient(options, poolOptions);
        promise.future().onComplete(ar -> httpClient.close());

        final int port = requestUri.getPort() != -1 ? requestUri.getPort() : (HTTPS_SCHEME.equals(requestUri.getScheme()) ? 443 : 80);

        try {
            final RequestOptions reqOptions = new RequestOptions()
                .setMethod(HttpMethod.GET)
                .setPort(port)
                .setHost(requestUri.getHost())
                .setURI(requestUri.toString())
                .putHeader(io.gravitee.common.http.HttpHeaders.USER_AGENT, gitHubFetcherConfiguration.getOwner())
                .putHeader("X-Gravitee-Request-Id", UUID.toString(UUID.random()))
                .putHeader("Accept", VERSION_HEADER)
                .setTimeout(httpClientTimeout)
                // Follow redirect since Gitlab may return a 3xx status code
                .setFollowRedirects(true);

            if (
                gitHubFetcherConfiguration.getUsername() != null &&
                !gitHubFetcherConfiguration.getUsername().trim().isEmpty() &&
                gitHubFetcherConfiguration.getPersonalAccessToken() != null &&
                !gitHubFetcherConfiguration.getPersonalAccessToken().trim().isEmpty()
            ) {
                String auth = gitHubFetcherConfiguration.getUsername() + ":" + gitHubFetcherConfiguration.getPersonalAccessToken();
                reqOptions.putHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(auth.getBytes()));
            }

            httpClient
                .request(reqOptions)
                .compose(HttpClientRequest::send)
                .compose(response -> handleResponse(url, response))
                .onSuccess(promise::complete)
                .onFailure(promise::fail);
        } catch (Exception ex) {
            promise.fail(ex);
        }

        return promise.future().toCompletionStage().toCompletableFuture();
    }

    private Future<Buffer> handleResponse(String url, HttpClientResponse response) {
        if (response.statusCode() == HttpStatusCode.OK_200) {
            return response.body();
        } else if (response.statusCode() == HttpStatusCode.NOT_FOUND_404) {
            return Future.failedFuture(new ResourceNotFoundException("Unable to fetch '" + url, null));
        } else {
            return Future.failedFuture(
                new FetcherException(
                    "Unable to fetch '" + url + "'. Status code: " + response.statusCode() + ". Message: " + response.statusMessage(),
                    null
                )
            );
        }
    }

    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }
}
