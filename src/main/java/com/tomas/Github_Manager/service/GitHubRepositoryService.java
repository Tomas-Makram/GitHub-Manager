package com.tomas.Github_Manager.service;

import com.tomas.Github_Manager.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.tomas.Github_Manager.service.GitHubApiClient.repoPath;

                //Repository details, issues, privacy settings, description, and deletion
@Service
public class GitHubRepositoryService {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositoryService.class);

    private final GitHubApiClient api;
    private final CacheEvictionService cacheEviction;

    public GitHubRepositoryService(GitHubApiClient api, CacheEvictionService cacheEviction) {
        this.api = api;
        this.cacheEviction = cacheEviction;
    }

    // =====================================================================
    // Reads — with @Cacheable
    // =====================================================================

    //Get details for a single warehouse (with caching)
    @Cacheable(value = CacheConfig.CACHE_REPO, key = "#username + ':' + #owner + ':' + #repo")
    public Map<String, Object> getRepository(String token, String username, String owner, String repo) {
        log.debug("Cache MISS — getRepository for {}/{}", owner, repo);
        return api.call(HttpMethod.GET, token, repoPath(owner, repo), null);
    }

    //It retrieves only open issues (excluding pull requests) and includes caching
    @Cacheable(value = CacheConfig.CACHE_ISSUES, key = "#username + ':' + #owner + ':' + #repo")
    public List<Map<String, Object>> getOpenIssues(String token, String username, String owner, String repo) {
        log.debug("Cache MISS — getOpenIssues for {}/{}", owner, repo);
        return api.callAllPages(token, repoPath(owner, repo) + "/issues?state=open").stream()
                .filter(issue -> !issue.containsKey("pull_request"))
                .collect(Collectors.toList());
    }

    // =====================================================================
    // Writes (without Cache)
    // =====================================================================

    //Changes the repository's privacy (public/private) without affecting the cache
    public void updateVisibility(String token, String owner, String repo, boolean isPrivate) {
        api.call(HttpMethod.PATCH, token, repoPath(owner, repo), Map.of("private", isPrivate));
    }

    //Changes the repository description without touching the cache
    public void updateDescription(String token, String owner, String repo, String newDescription) {
        api.call(HttpMethod.PATCH, token, repoPath(owner, repo), Map.of("description", newDescription));
    }

    //Deletes the repository from GitHub without touching the cache
    public void deleteRepository(String token, String owner, String repo) {
        api.call(HttpMethod.DELETE, token, repoPath(owner, repo), null);
    }

    // =====================================================================
    // Writes + Cache eviction
    // =====================================================================

    //It changes the privacy setting, then clears the repository cache and the list of repositories (since the privacy setting appears in both)
    public void changeVisibility(String token, String username, String owner, String repo, boolean isPrivate) {
        updateVisibility(token, owner, repo, isPrivate);
        cacheEviction.evictRepoCaches(username, owner, repo);
        cacheEviction.evictUserCaches(username);
    }

    //It updates the description and then clears the repository cache and the list of repositories (the description appears on the card on the home page)
    public void changeDescription(String token, String username, String owner, String repo, String newDescription) {
        updateDescription(token, owner, repo, newDescription);
        cacheEviction.evictRepoCaches(username, owner, repo);
        cacheEviction.evictUserCaches(username);
    }

    //Deletes the repository, logs the deletion, and clears all associated caches
    public void removeRepository(String token, String username, String owner, String repo) {
        deleteRepository(token, owner, repo);
        log.info("Repository {}/{} deleted", owner, repo);
        cacheEviction.evictRepoCaches(username, owner, repo);
        cacheEviction.evictUserCaches(username);
    }

    //It verifies that the name entered in the confirmation field matches the repository name exactly
    public boolean isDeleteConfirmed(String repoName, String confirmName) {
        return repoName.equals(confirmName);
    }

    //Clears the cache for a single repository so that its page fetches fresh data from GitHub
    public void refreshRepoCache(String username, String owner, String repo) {
        cacheEviction.evictRepoCaches(username, owner, repo);
        log.debug("Cache evicted for repo {}/{}", owner, repo);
    }
}