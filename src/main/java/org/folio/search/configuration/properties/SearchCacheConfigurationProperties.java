package org.folio.search.configuration.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "folio.cache")
public class SearchCacheConfigurationProperties {

  /**
   * Caffeine cache configuration as {@link String} for call-number browsing.
   */
  private String callNumberBrowseRangesCacheSpec;
}
