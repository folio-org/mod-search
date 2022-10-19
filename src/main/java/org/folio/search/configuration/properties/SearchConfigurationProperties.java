package org.folio.search.configuration.properties;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

import java.util.Map;
import java.util.Set;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import lombok.Data;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.types.IndexingDataFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "application.search-config")
public class SearchConfigurationProperties {

  /**
   * Provides list of initial languages for multi-language search.
   */
  @NotEmpty
  @Size(max = 5)
  private Set<String> initialLanguages = emptySet();

  /**
   * Provides the maximum number of supported languages.
   */
  @Min(1)
  @Max(50)
  private long maxSupportedLanguages = 5L;

  /**
   * Provides map with global features configuration. Can be overwritten by tenant configuration.
   */
  private Map<TenantConfiguredFeature, Boolean> searchFeatures = emptyMap();

  /**
   * Indexing settings for different resources.
   */
  private IndexingSettings indexing;

  @Data
  @Validated
  public static class IndexingSettings {

    /**
     * Instance subjects indexing settings.
     */
    private InstanceSubjectsIndexingSettings instanceSubjects;

    /**
     * Data format to use for passing data to elasticsearch.
     */
    private IndexingDataFormat dataFormat;
  }

  @Data
  @Validated
  public static class InstanceSubjectsIndexingSettings {

    /**
     * Retry attempts for delete bulk requests.
     */
    @Min(0)
    private int retryAttempts;
  }
}
