package org.folio.search.service;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.folio.search.configuration.properties.FolioEnvironment.getFolioEnvName;
import static org.folio.search.service.KafkaAdminService.KafkaTopic.getTenantTopicName;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.admin.NewTopic;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class KafkaAdminService {

  public static final String EVENT_LISTENER_ID = "mod-search-events-listener";
  private static final String KAFKA_TOPICS_FILE = "kafka/kafka-topics.json";

  private final KafkaAdmin kafkaAdmin;
  private final BeanFactory beanFactory;
  private final LocalFileProvider localFileProvider;
  private final FolioExecutionContext folioExecutionContext;
  private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
  private KafkaTopics kafkaTopics;

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
    var newTopics = new ArrayList<>(readTopics());
    var tenantId = folioExecutionContext.getTenantId();
    var topics = readAsTenantSpecificTopic(tenantId);
    newTopics.addAll(topics);

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
    log.info("Restarting kafka consumer to start listening created topics [id: {}]", EVENT_LISTENER_ID);
    var listenerContainer = kafkaListenerEndpointRegistry.getListenerContainer(EVENT_LISTENER_ID);
    listenerContainer.stop();
    listenerContainer.start();
  }

  private List<NewTopic> readTopics() {
    return getKafkaTopicsFromLocalConfig().getTopics().stream()
      .map(KafkaTopic::toKafkaTopic)
      .collect(toList());
  }

  private List<NewTopic> readAsTenantSpecificTopic(String tenantId) {
    return getKafkaTopicsFromLocalConfig().getTopics().stream()
      .map(topic -> topic.toKafkaTopic(tenantId))
      .collect(toList());
  }

  private KafkaTopics getKafkaTopicsFromLocalConfig() {
    if (kafkaTopics == null) {
      kafkaTopics = localFileProvider.readAsObject(KAFKA_TOPICS_FILE, KafkaTopics.class);
      return kafkaTopics;
    }
    return kafkaTopics;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(staticName = "of")
  static class KafkaTopics {

    private List<KafkaTopic> topics = emptyList();
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(staticName = "of")
  static class KafkaTopic {

    private String name;
    private Integer numPartitions = 1;
    private Short replicationFactor = 1;

    NewTopic toKafkaTopic() {
      return new NewTopic(name, numPartitions, replicationFactor);
    }

    NewTopic toKafkaTopic(String tenantId) {
      return new NewTopic(getTenantTopicName(name, tenantId), numPartitions, replicationFactor);
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
