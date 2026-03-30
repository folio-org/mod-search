package org.folio.search.configuration.kafka;

import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.event.InstanceSharingCompleteEvent;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

@Configuration
@RequiredArgsConstructor
public class InstanceSharingCompleteEventKafkaConfiguration extends KafkaConfiguration {

  private final KafkaProperties kafkaProperties;

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, InstanceSharingCompleteEvent>
    instanceSharingCompletedListenerContainerFactory() {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, InstanceSharingCompleteEvent>();
    var deserializer = new JacksonJsonDeserializer<>(InstanceSharingCompleteEvent.class, false);
    var overrideProperties = Map.<String, Object>of(MAX_POLL_RECORDS_CONFIG, 10);
    factory.setConsumerFactory(getConsumerFactory(deserializer, kafkaProperties, overrideProperties));
    return factory;
  }
}
