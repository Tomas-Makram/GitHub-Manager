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

                        //User data, list of their repositories, and creation of a new repository
@Service
public class GitHubUserService {

    private static final Logger log = LoggerFactory.getLogger(GitHubUserService.class);

    private final GitHubApiClient api;
    private final CacheEvictionService cacheEviction;

    public GitHubUserService(GitHubApiClient api, CacheEvictionService cacheEviction) {
        this.api = api;
        this.cacheEviction = cacheEviction;
    }

    //Fetches user data from GitHub (with caching)
    //It comes from client.getPrincipalName() in the Controller
    @Cacheable(value = CacheConfig.CACHE_PROFILE, key = "#username")
    public Map<String, Object> getUserProfile(String token, String username) {
        log.debug("Cache MISS — getUserProfile for {}", username);
        return api.call(HttpMethod.GET, token, "/user", null);
    }

    //Retrieves all of a user's repositories (with caching), using pagination
    @Cacheable(value = CacheConfig.CACHE_REPOS, key = "#username")
    public List<Map<String, Object>> getUserRepositories(String token, String username) {
        log.debug("Cache MISS — getUserRepositories for {}", username);
        return api.callAllPages(token,
                "/user/repos?visibility=all&affiliation=owner,collaborator,organization_member&sort=updated");
    }

    //Creates a new repository (with an initial README)
    // Then clears the list of repositories from the cache
    public void createRepository(String token, String username, String name, String description, boolean isPrivate) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("description", description);
        body.put("private", isPrivate);
        body.put("auto_init", true);
        api.call(HttpMethod.POST, token, "/user/repos", body);
        cacheEviction.evictUserCaches(username);
    }

    //Clears the user's cache so that the home page fetches fresh data from GitHub
    public void refreshUserCache(String username) {
        cacheEviction.evictUserCaches(username);
        log.debug("Cache evicted for user {}", username);
    }
}