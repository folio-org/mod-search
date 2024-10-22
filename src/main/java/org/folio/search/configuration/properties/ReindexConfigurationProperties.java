package org.folio.search.configuration.properties;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@ConfigurationProperties(prefix = "folio.reindex")
public class ReindexConfigurationProperties {

  /**
   * Defines number of locations to retrieve per inventory http request on locations reindex process.
   */
  private Integer locationBatchSize = 1_000;

  private Integer uploadRangeSize = 1_000;

  @Min(1)
  private Integer uploadRangeLevel = 3;

  private Integer mergeRangeSize = 1_000;

  private Integer mergeRangePublisherCorePoolSize = 3;

  private Integer mergeRangePublisherMaxPoolSize = 6;

  private long mergeRangePublisherRetryIntervalMs = 1000;

  private int mergeRangePublisherRetryAttempts = 5;
}
