package org.folio.search.configuration.properties;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@ConfigurationProperties(prefix = "spring.cache")
public class CacheConfigurationProperties {

  /**
   * List of cache names to create.
   */
  private List<String> cacheNames;

  /**
   * Caffeine cache specification for default caches.
   */
  private Caffeine caffeine = new Caffeine();

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Caffeine {
    /**
     * Caffeine spec string (e.g., "maximumSize=500,expireAfterWrite=3600s").
     */
    private String spec = "maximumSize=500,expireAfterWrite=3600s";
  }
}
