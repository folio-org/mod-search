package org.folio.search.service;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.folio.search.utils.KafkaUtils.getTenantTopicName;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.admin.NewTopic;
import org.folio.search.configuration.properties.FolioKafkaProperties;
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
  public static final String AUTHORITY_LISTENER_ID = "mod-search-authorities-listener";
  public static final String CONTRIBUTOR_LISTENER_ID = "mod-search-contributor-listener";

  private final KafkaAdmin kafkaAdmin;
  private final BeanFactory beanFactory;
  private final FolioExecutionContext folioExecutionContext;
  private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
  private final FolioKafkaProperties kafkaProperties;

  /**
   * Creates kafka topics using existing configuration in application.kafka.topics.
   */
  public void createKafkaTopics() {
    var configTopics = kafkaProperties.getTopics();
    var tenantId = folioExecutionContext.getTenantId();
    var newTopics = toTenantSpecificTopic(configTopics, tenantId);

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
        log.info("Restarting kafka consumer to start listening created topics [ids: {}]",
          container.getListenerId());
        container.stop();
        container.start();
      }
    );
  }

  private List<NewTopic> toTenantSpecificTopic(List<FolioKafkaProperties.KafkaTopic> localConfigTopics,
                                               String tenantId) {
    return localConfigTopics.stream()
      .map(topic -> toKafkaTopic(topic, tenantId))
      .collect(toList());
  }

  private NewTopic toKafkaTopic(FolioKafkaProperties.KafkaTopic topic, String tenantId) {
    return new NewTopic(
      getTenantTopicName(topic.getName(), tenantId),
      ofNullable(topic.getNumPartitions()),
      ofNullable(topic.getReplicationFactor())
    );
  }
}
