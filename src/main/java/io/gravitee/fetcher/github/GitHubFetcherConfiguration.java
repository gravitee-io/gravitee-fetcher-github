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

import io.gravitee.fetcher.api.FetcherConfiguration;
import io.gravitee.fetcher.api.FilepathAwareFetcherConfiguration;
import io.gravitee.fetcher.api.Sensitive;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GitHubFetcherConfiguration implements FetcherConfiguration, FilepathAwareFetcherConfiguration {

    private String githubUrl;
    private boolean useSystemProxy;
    private String owner;
    private String repository;
    private String branchOrTag;
    private String filepath = "/path/to/file";
    private String username;

    @Sensitive
    private String personalAccessToken;

    private String editLink;

    private String fetchCron;

    private boolean autoFetch = false;

    public String getGithubUrl() {
        return githubUrl;
    }

    public void setGithubUrl(String githubUrl) {
        this.githubUrl = githubUrl;
    }

    public boolean isUseSystemProxy() {
        return useSystemProxy;
    }

    public void setUseSystemProxy(boolean useSystemProxy) {
        this.useSystemProxy = useSystemProxy;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getBranchOrTag() {
        return branchOrTag;
    }

    public void setBranchOrTag(String branchOrTag) {
        this.branchOrTag = branchOrTag;
    }

    @Override
    public String getFilepath() {
        return filepath;
    }

    @Override
    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPersonalAccessToken() {
        return personalAccessToken;
    }

    public void setPersonalAccessToken(String personalAccessToken) {
        this.personalAccessToken = personalAccessToken;
    }

    public String getEditLink() {
        return editLink;
    }

    public void setEditLink(String editLink) {
        this.editLink = editLink;
    }

    @Override
    public String getFetchCron() {
        return fetchCron;
    }

    public void setFetchCron(String fetchCron) {
        this.fetchCron = fetchCron;
    }

    @Override
    public boolean isAutoFetch() {
        return autoFetch;
    }

    public void setAutoFetch(boolean autoFetch) {
        this.autoFetch = autoFetch;
    }
}
