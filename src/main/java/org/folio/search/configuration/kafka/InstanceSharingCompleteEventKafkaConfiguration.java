package org.folio.search.configuration.kafka;

import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.event.InstanceSharingCompleteEvent;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

@Configuration
@RequiredArgsConstructor
public class InstanceSharingCompleteEventKafkaConfiguration extends KafkaConfiguration {

  private final KafkaProperties kafkaProperties;

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, InstanceSharingCompleteEvent>
    instanceSharingCompleteListenerContainerFactory(CommonErrorHandler commonErrorHandler) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, InstanceSharingCompleteEvent>();
    var deserializer = new JacksonJsonDeserializer<>(InstanceSharingCompleteEvent.class, false);
    factory.setConsumerFactory(getConsumerFactory(deserializer, kafkaProperties, Collections.emptyMap()));
    factory.setCommonErrorHandler(commonErrorHandler);
    return factory;
  }
}
