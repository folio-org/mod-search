package org.folio.search.configuration.properties;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Application properties for kafka message consumer.
 */
@Data
@Component
@ConfigurationProperties("application.kafka")
public class FolioKafkaProperties {

  /**
   * Map with settings for application kafka listeners.
   */
  private Map<String, KafkaListenerProperties> listener;

  /**
   * Specifies time to wait before reattempting delivery.
   */
  private long retryIntervalMs = 1000;

  /**
   * How many delivery attempts to perform when message failed.
   */
  private long retryDeliveryAttempts = 5;

  /**
   * What topics should be created by mod-search.
   */
  private List<KafkaTopic> topics;

  /**
   * Contains set of settings for specific kafka listener.
   */
  @Data
  public static class KafkaListenerProperties {

    /**
     * List of topic to listen.
     */
    private String topicPattern;

    /**
     * Number of concurrent consumers in service.
     */
    private Integer concurrency = 5;

    /**
     * The group id.
     */
    private String groupId;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(staticName = "of")
  public static class KafkaTopic {

    /**
     * Topic name.
     */
    private String name;

    /**
     * Number of partitions for topic as spring placeholder expression.
     */
    private Integer numPartitions;

    /**
     * Replication factor for topic as spring placeholder expression.
     */
    private Short replicationFactor;
  }
}
