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

  /**
   * Controls filtering of reindex Kafka events by the reindex job identifier of this instance.
   */
  private JobFilter jobFilter = new JobFilter();

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class JobFilter {

    /**
     * Enables comparison of the reindex job identifier carried by reindex events with the active job of the tenant.
     * When disabled every event is processed, as before job tracking was introduced.
     */
    private boolean enabled = true;

    /**
     * Defines what to do with reindex events that carry no job identifier,
     * e.g. events published by a mod-inventory-storage version that does not propagate it.
     */
    private MissingJobIdPolicy missingJobIdPolicy = MissingJobIdPolicy.PROCESS;
  }

  public enum MissingJobIdPolicy {
    /**
     * Process events without a job identifier (legacy-compatible behavior for standard instances).
     */
    PROCESS,
    /**
     * Ignore events without a job identifier (strict mode for background reindex instances).
     */
    IGNORE
  }
}
