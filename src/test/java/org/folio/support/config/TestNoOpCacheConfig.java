package org.folio.support.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestNoOpCacheConfig {
  @Bean
  public CacheManager cacheManager() {
    return new NoOpCacheManager();
  }
}
