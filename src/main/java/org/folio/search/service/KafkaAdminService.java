package org.folio.search.service;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class KafkaAdminService {

  private static final String KAFKA_TOPICS_FILE = "kafka/kafka-topics.json";

  private final LocalFileProvider localFileProvider;
  private final KafkaAdmin kafkaAdminClient;
  private final BeanFactory beanFactory;

  public void createKafkaTopics() {
    var newTopics = readTopics();
    log.info("Creating topics for kafka [topics: {}]", newTopics);
    var configurableBeanFactory = (ConfigurableBeanFactory) beanFactory;
    newTopics.forEach(newTopic -> {
      var beanName = newTopic.name() + ".topic";
      if (!configurableBeanFactory.containsBean(beanName)) {
        configurableBeanFactory.registerSingleton(beanName, newTopic);
      }
    });
    kafkaAdminClient.initialize();
  }

  private List<NewTopic> readTopics() {
    var kafkaTopics = localFileProvider.readAsObject(KAFKA_TOPICS_FILE, KafkaTopics.class);
    return kafkaTopics.getTopics().stream()
      .map(KafkaTopic::toKafkaTopic)
      .collect(Collectors.toList());
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
  }
}
