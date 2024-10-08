package org.folio.search.configuration.kafka;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.ResourceEvent;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
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
    BatchInterceptor<String, ResourceEvent>[] batchInterceptors) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, ResourceEvent>();
    factory.setBatchListener(true);
    var deserializer = new JsonDeserializer<>(ResourceEvent.class, false);
    factory.setConsumerFactory(getConsumerFactory(deserializer, kafkaProperties));
    factory.setBatchInterceptor(new CompositeBatchInterceptor<>(batchInterceptors));
    return factory;
  }
}
