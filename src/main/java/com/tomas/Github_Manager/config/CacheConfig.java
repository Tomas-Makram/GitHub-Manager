package com.tomas.Github_Manager.config;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

                                // Manager Caching

@Configuration
@EnableCaching
public class CacheConfig {

    // Cache Bucket Names — Constants for interaction between the service and the controller
    public static final String CACHE_PROFILE  = "github-profile";
    public static final String CACHE_REPOS    = "github-repos";
    public static final String CACHE_REPO     = "github-repo";
    public static final String CACHE_BRANCHES = "github-branches";
    public static final String CACHE_ISSUES   = "github-issues";
    public static final String CACHE_README   = "github-readme";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(

            // General user data
            buildCache(CACHE_PROFILE,  10, 200),

            // List of repos
            buildCache(CACHE_REPOS,     5, 200),

            // Details for a single repository (repo info, visibility, description…)
            buildCache(CACHE_REPO,      5, 500),

            // Branches for each warehouse
            buildCache(CACHE_BRANCHES,  5, 500),

            // The issues
            buildCache(CACHE_ISSUES,    3, 500),

            // The README
            buildCache(CACHE_README,   10, 300)
        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, long duration, long maxSize) {
        return new CaffeineCache(name,
            Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(duration, TimeUnit.MINUTES)
                .recordStats()
                .build());
    }
}
