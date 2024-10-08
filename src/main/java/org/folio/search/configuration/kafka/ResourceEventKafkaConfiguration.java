package org.folio.search.configuration.kafka;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.integration.message.interceptor.CompositeRecordFilterStrategy;
import org.folio.search.integration.message.interceptor.ResourceEventBatchInterceptor;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
@RequiredArgsConstructor
public class ResourceEventKafkaConfiguration extends KafkaConfiguration {

  private final KafkaProperties kafkaProperties;

  /**
   * Creates and configures {@link ConcurrentKafkaListenerContainerFactory} as Spring bean
   * for consuming resource events from Apache Kafka.
   *
   * @return {@link ConcurrentKafkaListenerContainerFactory} object as Spring bean.
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, ResourceEvent> resourceListenerContainerFactory(
    RecordFilterStrategy<String, ResourceEvent>[] recordFilterStrategies,
    ResourceEventBatchInterceptor resourceEventBatchInterceptor) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, ResourceEvent>();
    factory.setBatchListener(true);
    var deserializer = new JsonDeserializer<>(ResourceEvent.class, false);
    factory.setBatchInterceptor(resourceEventBatchInterceptor);
    factory.setConsumerFactory(getConsumerFactory(deserializer, kafkaProperties));
    factory.setRecordFilterStrategy(new CompositeRecordFilterStrategy<>(recordFilterStrategies));
    return factory;
  }

  /**
   * Creates and configures {@link KafkaTemplate} as Spring bean.
   *
   * <p>Key type - {@link String}, value - {@link ResourceEvent}.</p>
   *
   * @return typed {@link KafkaTemplate} object as Spring bean.
   */
  @Bean
  public KafkaTemplate<String, ResourceEvent> resourceKafkaTemplate() {
    return new KafkaTemplate<>(getProducerFactory(kafkaProperties));
  }
}
