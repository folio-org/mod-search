package org.folio.search.configuration.properties;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for index management operations.
 */
@Data
@Component
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@ConfigurationProperties(prefix = "folio.index-management")
public class IndexManagementConfigurationProperties {

  /**
   * Batch size for delete-by-query operations to improve performance on large datasets.
   */
  @Min(1)
  private Integer deleteQueryBatchSize = 1_000;

  /**
   * Scroll timeout in minutes for delete-by-query operations to handle large result sets.
   */
  @Min(1)
  private Integer deleteQueryScrollTimeoutMinutes = 5;

  /**
   * Request timeout in minutes for delete-by-query operations to prevent failures on large operations.
   */
  @Min(1)
  private Integer deleteQueryRequestTimeoutMinutes = 30;

  /**
   * Whether to refresh the index after delete-by-query operation.
   * Setting to false improves performance by deferring refresh.
   */
  private Boolean deleteQueryRefresh = false;
}
