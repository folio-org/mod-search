package org.folio.search.configuration;

import static org.folio.search.configuration.SearchCacheNames.REINDEX_TARGET_TENANT_CACHE;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.folio.search.configuration.properties.CacheConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfiguration {

  private final CacheConfigurationProperties cacheProperties;

  @Bean
  public CacheManager cacheManager() {
    var cacheManager = new CaffeineCacheManager();

    // Set cache names from configuration
    if (cacheProperties.getCacheNames() != null && !cacheProperties.getCacheNames().isEmpty()) {
      cacheManager.setCacheNames(cacheProperties.getCacheNames());
    }

    // Set default spec from configuration for all caches
    var defaultSpec = cacheProperties.getCaffeine().getSpec();
    cacheManager.setCaffeineSpec(CaffeineSpec.parse(defaultSpec));

    // Register custom cache with 10-second TTL for reindex-target-tenant
    cacheManager.registerCustomCache(REINDEX_TARGET_TENANT_CACHE,
        Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .build());

    return cacheManager;
  } //todo: reindex status endpoint version bump
} //todo: full reindex endpoint version bump
