package org.folio.search.service;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.folio.search.configuration.properties.FolioEnvironment.getFolioEnvName;
import static org.folio.search.service.KafkaAdminService.KafkaTopic.getTenantTopicName;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.admin.NewTopic;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.env.PropertyResolver;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class KafkaAdminService {

  public static final String EVENT_LISTENER_ID = "mod-search-events-listener";
  public static final String AUTHORITY_LISTENER_ID = "mod-search-authorities-listener";
  private static final String KAFKA_TOPICS_FILE = "kafka/kafka-topics.json";

  private final KafkaAdmin kafkaAdmin;
  private final BeanFactory beanFactory;
  private final PropertyResolver propertyResolver;
  private final LocalFileProvider localFileProvider;
  private final FolioExecutionContext folioExecutionContext;
  private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

  /**
   * Returns list of tenant related topics names.
   *
   * @return list of tenant specific topics names as {@link String} object
   */
  public List<String> getDefaultTenantKafkaTopics() {
    var tenantId = folioExecutionContext.getTenantId();
    return getKafkaTopicsFromLocalConfig().getTopics().stream()
      .map(KafkaTopic::getName)
      .map(topicName -> getTenantTopicName(topicName, tenantId))
      .collect(toList());
  }

  /**
   * Creates kafka topics using existing configuration in kafka/kafka-topics.json.
   */
  public void createKafkaTopics() {
    var configTopics = getKafkaTopicsFromLocalConfig();
    var tenantId = folioExecutionContext.getTenantId();
    var newTopics = readAsTenantSpecificTopic(configTopics, tenantId);

    log.info("Creating topics for kafka [topics: {}]", newTopics);
    var configurableBeanFactory = (ConfigurableBeanFactory) beanFactory;
    newTopics.forEach(newTopic -> {
      var beanName = newTopic.name() + ".topic";
      if (!configurableBeanFactory.containsBean(beanName)) {
        configurableBeanFactory.registerSingleton(beanName, newTopic);
      }
    });
    kafkaAdmin.initialize();
  }

  /**
   * Restarts kafka event listeners in mod-search application.
   */
  public void restartEventListeners() {
    kafkaListenerEndpointRegistry.getAllListenerContainers().forEach(container -> {
        log.info("Restarting kafka consumer to start listening created topics [ids: {}]", container.getListenerId());
        container.stop();
        container.start();
      }
    );
  }

  private List<NewTopic> readAsTenantSpecificTopic(KafkaTopics localConfigTopics, String tenantId) {
    return localConfigTopics.getTopics().stream()
      .map(topic -> topic.toKafkaTopic(tenantId))
      .collect(toList());
  }

  private KafkaTopics getKafkaTopicsFromLocalConfig() {
    var kafkaTopics = localFileProvider.readAsObject(KAFKA_TOPICS_FILE, KafkaTopics.class);
    for (KafkaTopic topic : kafkaTopics.getTopics()) {
      topic.setNumPartitions(propertyResolver.resolveRequiredPlaceholders(topic.getNumPartitions()));
      topic.setReplicationFactor(propertyResolver.resolveRequiredPlaceholders(topic.getReplicationFactor()));
    }
    return kafkaTopics;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(staticName = "of")
  static class KafkaTopics {

    /**
     * List of {@link KafkaTopic} descriptors.
     */
    private List<KafkaTopic> topics = emptyList();
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(staticName = "of")
  static class KafkaTopic {

    /**
     * Topic name.
     */
    private String name;

    /**
     * Number of partitions for topic as spring placeholder expression.
     */
    private String numPartitions;

    /**
     * Replication factor for topic as spring placeholder expression.
     */
    private String replicationFactor;

    /**
     * Transforms {@link KafkaTopic} to the {@link NewTopic} object.
     *
     * @param tenantId - tenant id as {@link String} object
     * @return created {@link NewTopic} object
     */
    NewTopic toKafkaTopic(String tenantId) {
      return new NewTopic(
        getTenantTopicName(name, tenantId),
        ofNullable(numPartitions).filter(StringUtils::isNotBlank).map(Integer::parseInt),
        ofNullable(replicationFactor).filter(StringUtils::isNotBlank).map(Short::parseShort)
      );
    }

    /**
     * Returns topic name in the format - `{env}.{tenant}.inventory.{resource-type}`
     *
     * @param initialName initial topic name as {@link String}
     * @param tenantId tenant id as {@link String}
     * @return topic name as {@link String} object
     */
    static String getTenantTopicName(String initialName, String tenantId) {
      return String.format("%s.%s.%s", getFolioEnvName(), tenantId, initialName);
    }
  }
}
