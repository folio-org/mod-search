package org.folio.search.configuration.properties;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.types.IndexingDataFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "folio.search-config")
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
   * Provides the maximum number offset for additional elasticsearch requests on browse around.
   */
  private long maxBrowseRequestOffset = 500L;

  /**
   * Provides the maximum number of IDs for performing search with batch requests.
   */
  private long maxSearchBatchRequestIdsCount = 20_000L;

  /**
   * Provides the size parameter for querying consortium records (holdings or items).
   */
  private int searchConsortiumRecordsPageSize = 5_000;

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
     * Data format to use for passing data to elasticsearch.
     */
    private IndexingDataFormat dataFormat;

    private long instanceChildrenIndexDelayMs;
  }

}
