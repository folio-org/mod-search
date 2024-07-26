package org.folio.search.configuration.kafka;

import static org.folio.search.configuration.kafka.KafkaConfiguration.SearchTopic.CONSORTIUM_INSTANCE;

import lombok.RequiredArgsConstructor;
import org.folio.search.model.event.ConsortiumInstanceEvent;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
@RequiredArgsConstructor
public class ConsortiumInstanceEventKafkaConfiguration extends KafkaConfiguration {

  private final KafkaProperties kafkaProperties;

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, ConsortiumInstanceEvent> consortiumListenerContainerFactory() {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, ConsortiumInstanceEvent>();
    factory.setBatchListener(true);
    var deserializer = new JsonDeserializer<>(ConsortiumInstanceEvent.class);
    factory.setConsumerFactory(getConsumerFactory(deserializer, kafkaProperties));
    return factory;
  }

  @Bean
  public KafkaTemplate<String, ConsortiumInstanceEvent> consortiumKafkaTemplate() {
    return new KafkaTemplate<>(getProducerFactory(kafkaProperties));
  }

  @Bean
  public FolioMessageProducer<ConsortiumInstanceEvent> consortiumMessageProducer() {
    var producer = new FolioMessageProducer<>(consortiumKafkaTemplate(), CONSORTIUM_INSTANCE);
    producer.setKeyMapper(ConsortiumInstanceEvent::getInstanceId);
    return producer;
  }
}
