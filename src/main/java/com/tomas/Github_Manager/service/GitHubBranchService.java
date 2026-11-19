package com.tomas.Github_Manager.service;

import com.tomas.Github_Manager.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.tomas.Github_Manager.service.GitHubApiClient.encodeBranch;
import static com.tomas.Github_Manager.service.GitHubApiClient.nestedString;
import static com.tomas.Github_Manager.service.GitHubApiClient.repoPath;

                                        // Branch management
@Service
public class GitHubBranchService {

    private static final Logger log = LoggerFactory.getLogger(GitHubBranchService.class);

    private final GitHubApiClient api;
    private final CacheEvictionService cacheEviction;

    public GitHubBranchService(GitHubApiClient api, CacheEvictionService cacheEviction) {
        this.api = api;
        this.cacheEviction = cacheEviction;
    }

    //Retrieves all warehouse branches (with caching)
    // @param username The username — used as part of the cache key to ensure isolation between users
    @Cacheable(value = CacheConfig.CACHE_BRANCHES, key = "#username + ':' + #owner + ':' + #repo")
    public List<Map<String, Object>> getBranches(String token, String username, String owner, String repo) {
        log.debug("Cache MISS — getBranches for {}/{}", owner, repo);
        return api.callAllPages(token, repoPath(owner, repo) + "/branches");
    }

    //Creates a new branch from the repository's default branch, then clears the repository's cache
    public void createBranch(String token, String username, String owner, String repo, String newBranchName) {
        Map<String, Object> repoInfo = api.call(HttpMethod.GET, token, repoPath(owner, repo), null);
        String defaultBranch = (String) repoInfo.get("default_branch");
        createBranchFromDefault(token, owner, repo, defaultBranch, newBranchName);
        cacheEviction.evictRepoCaches(username, owner, repo);
    }

    //Deletes a branch, then clears the repository cache
    public void deleteBranch(String token, String username, String owner, String repo, String branchName) {
        api.call(HttpMethod.DELETE, token,
                repoPath(owner, repo) + "/git/refs/heads/" + encodeBranch(branchName), null);
        cacheEviction.evictRepoCaches(username, owner, repo);
    }

    //Renames a branch, then clears the repository cache
    public void renameBranch(String token, String username, String owner, String repo, String oldName, String newName) {
        api.call(HttpMethod.POST, token,
                repoPath(owner, repo) + "/branches/" + encodeBranch(oldName) + "/rename",
                Map.of("new_name", newName));
        cacheEviction.evictRepoCaches(username, owner, repo);
    }

    //Fetches data for a single branch directly from GitHub (bypassing the cache)
    //Also used by the deployment service
    public Map<String, Object> getBranch(String token, String owner, String repo, String branch) {
        return api.call(HttpMethod.GET, token, repoPath(owner, repo) + "/branches/" + encodeBranch(branch), null);
    }


    //Creates a new branch that points to the latest commit on the default branch
    public void createBranchFromDefault(String token, String owner, String repo,
                                        String defaultBranch, String targetBranch) {
        Map<String, Object> defaultInfo = getBranch(token, owner, repo, defaultBranch);
        String latestCommitSha = nestedString(defaultInfo, "commit", "sha");

        Map<String, Object> body = new HashMap<>();
        body.put("ref", "refs/heads/" + targetBranch);
        body.put("sha", latestCommitSha);
        api.call(HttpMethod.POST, token, repoPath(owner, repo) + "/git/refs", body);
    }
}