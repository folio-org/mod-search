package org.folio.search.configuration.kafka;

import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG;
import static org.folio.search.configuration.kafka.KafkaConfiguration.SearchTopic.INDEX_SUB_RESOURCE;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.event.SubResourceEvent;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
@RequiredArgsConstructor
public class SubResourceKafkaConfiguration extends KafkaConfiguration {

  private final KafkaProperties kafkaProperties;

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, SubResourceEvent> subResourceListenerContainerFactory(
    @Value("#{folioKafkaProperties.listener['index-sub-resource'].maxPollRecords}") Integer maxPollRecords,
    @Value("#{folioKafkaProperties.listener['index-sub-resource'].maxPollIntervalMs}") Integer maxPollIntervalMs) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, SubResourceEvent>();
    factory.setBatchListener(true);
    var deserializer = new JsonDeserializer<>(SubResourceEvent.class, false);
    var overrideProperties = Map.<String, Object>of(MAX_POLL_RECORDS_CONFIG, maxPollRecords,
      MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
    factory.setConsumerFactory(getConsumerFactory(deserializer, kafkaProperties, overrideProperties));
    return factory;
  }

  @Bean
  public KafkaTemplate<String, SubResourceEvent> subResourceKafkaTemplate() {
    return new KafkaTemplate<>(getProducerFactory(kafkaProperties));
  }

  @Bean
  public FolioMessageProducer<SubResourceEvent> subResourceMessageProducer() {
    return new FolioMessageProducer<>(subResourceKafkaTemplate(), INDEX_SUB_RESOURCE);
  }
}
