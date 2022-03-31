package org.folio.search.configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import org.folio.search.configuration.properties.SearchCacheConfigurationProperties;
import org.folio.search.model.service.CallNumberBrowseRangeValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfiguration {

  /**
   * Creates a {@link Cache} bean for call-number browsing optimization.
   *
   * @return created {@link Cache} bean
   */
  @Bean
  public Cache<String, List<CallNumberBrowseRangeValue>> callNumberRangesCache(
    SearchCacheConfigurationProperties configuration) {
    return Caffeine.from(configuration.getCallNumberBrowseRangesCacheSpec()).build();
  }
}
