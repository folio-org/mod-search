package org.folio.search.configuration.kafka;

import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.event.IndexInstanceEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.kafka.listener.CompositeBatchInterceptor;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
@RequiredArgsConstructor
public class InstanceResourceEventKafkaConfiguration extends KafkaConfiguration {

  private final KafkaProperties kafkaProperties;

  /**
   * Creates and configures {@link ConcurrentKafkaListenerContainerFactory} as Spring bean
   * for consuming resource instance related events from Kafka.
   *
   * @return {@link ConcurrentKafkaListenerContainerFactory} object as Spring bean.
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, ResourceEvent> instanceResourceListenerContainerFactory(
    BatchInterceptor<String, ResourceEvent>[] batchInterceptors,
    @Value("#{folioKafkaProperties.listener['events'].maxPollRecords}") Integer maxPollRecords,
    @Value("#{folioKafkaProperties.listener['events'].maxPollIntervalMs}") Integer maxPollIntervalMs) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, ResourceEvent>();
    factory.setBatchListener(true);
    var deserializer = new JsonDeserializer<>(ResourceEvent.class, false);
    var overrideProperties = Map.<String, Object>of(MAX_POLL_RECORDS_CONFIG, maxPollRecords,
      MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
    factory.setConsumerFactory(getConsumerFactory(deserializer, kafkaProperties, overrideProperties));
    factory.setBatchInterceptor(new CompositeBatchInterceptor<>(batchInterceptors));
    return factory;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, IndexInstanceEvent> indexInstanceListenerContainerFactory(
    @Value("#{folioKafkaProperties.listener['index-instance'].maxPollRecords}") Integer maxPollRecords,
    @Value("#{folioKafkaProperties.listener['index-instance'].maxPollIntervalMs}") Integer maxPollIntervalMs) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, IndexInstanceEvent>();
    factory.setBatchListener(true);
    var deserializer = new JsonDeserializer<>(IndexInstanceEvent.class, false);
    var overrideProperties = Map.<String, Object>of(MAX_POLL_RECORDS_CONFIG, maxPollRecords,
      MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
    factory.setConsumerFactory(getConsumerFactory(deserializer, kafkaProperties, overrideProperties));
    return factory;
  }

  @Bean
  public KafkaTemplate<String, IndexInstanceEvent> indexInstanceKafkaTemplate() {
    return new KafkaTemplate<>(getProducerFactory(kafkaProperties));
  }
}
