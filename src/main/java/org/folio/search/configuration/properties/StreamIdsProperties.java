package org.folio.search.configuration.properties;

import jakarta.validation.constraints.Max;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties("folio.stream-ids")
public class StreamIdsProperties {

  /**
   * Max number of entities in es response.
   */
  @Max(value = 10_000)
  private int scrollQuerySize = 1000;

  /**
   * Specifies time to wait before reattempting search request.
   */
  private long retryIntervalMs = 1000;

  /**
   * How many delivery attempts to perform when a search request failed.
   */
  private int retryAttempts = 3;

  /**
   * ThreadPoolExecutor's core pool size.
   */
  private int corePoolSize = 2;

  /**
   * ThreadPoolExecutor's max pool size.
   */
  private int maxPoolSize = 2;

  /**
   * LinkedBlockingQueue capacity for the ThreadPoolExecutor.
   */
  private int queueCapacity = 500;

  /**
   * Number of days after which the job will be considered expired and cleaned up.
   */
  private int jobExpirationDays = 7;
}
