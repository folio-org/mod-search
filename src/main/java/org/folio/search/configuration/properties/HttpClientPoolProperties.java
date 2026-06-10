package org.folio.search.configuration.properties;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Connection-pool configuration for the shared {@code RestClient} that backs all Folio
 * {@link org.springframework.web.service.annotation.HttpExchange} clients (e.g. the
 * {@code InventoryReindexRecordsClient} used during the reindex merge phase).
 *
 * <p>The defaults provided by folio-spring-base use the un-tuned Apache HttpClient pool
 * (a handful of connections per route), which serializes the many concurrent publisher
 * threads that trigger reindex record ranges on mod-inventory-storage. These properties
 * raise the pool limits so the available parallelism is actually usable.</p>
 *
 * <p><b>Note:</b> no socket/response (read) timeout is configured on purpose. Reindex
 * publish/export calls to mod-inventory-storage are long-running and must not be
 * interrupted by a read timeout.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "folio.exchange.http-client")
public class HttpClientPoolProperties {

  /**
   * Maximum number of connections per route. All clients call mod-inventory-storage through the
   * same Okapi host, so requests share a single route — this is the effective concurrency limit
   * and should be greater than or equal to {@code REINDEX_MERGE_RANGE_PUBLISHER_MAX_POOL_SIZE}.
   */
  private int maxConnectionsPerRoute = 50;

  /**
   * Maximum total number of connections in the pool. Should be greater than or equal to
   * {@link #maxConnectionsPerRoute}.
   */
  private int maxConnectionsTotal = 100;

  /**
   * Timeout for establishing a TCP connection. This only bounds connection setup and does not
   * limit how long an established (long-running) request may take.
   */
  private Duration connectTimeout = Duration.ofSeconds(10);

  /**
   * Optional maximum lifetime of pooled connections. When {@code null}, connections are reused
   * without a fixed time-to-live.
   */
  private Duration connectionTimeToLive;

  /**
   * Optional interval after which idle pooled connections are validated before reuse. When
   * {@code null}, the Apache HttpClient default is used.
   */
  private Duration validateAfterInactivity;
}

