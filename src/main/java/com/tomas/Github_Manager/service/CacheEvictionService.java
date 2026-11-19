package com.tomas.Github_Manager.service;
import com.tomas.Github_Manager.config.CacheConfig;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

// Clears cache items after any write operation or when the user requests an update

@Service
public class CacheEvictionService {

    private final CacheManager cacheManager;

    public CacheEvictionService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    // Clears all cache related to the user (list of repositories and profile)
    public void evictUserCaches(String username) {
        evict(CacheConfig.CACHE_PROFILE, username);
        evict(CacheConfig.CACHE_REPOS, username);
    }

    // Clears all cache associated with a specific repository
    public void evictRepoCaches(String username, String owner, String repo) {

        //The key includes the username to ensure cache isolation between different users
        String key = username + ":" + owner + ":" + repo;
        evict(CacheConfig.CACHE_REPO, key);
        evict(CacheConfig.CACHE_BRANCHES, key);
        evict(CacheConfig.CACHE_ISSUES, key);
        evict(CacheConfig.CACHE_README, key);
    }

    // Deletes a single key from the cache by its name; if the cache does not exist, it does nothing
    private void evict(String cacheName, Object key) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) cache.evict(key);
    }
}