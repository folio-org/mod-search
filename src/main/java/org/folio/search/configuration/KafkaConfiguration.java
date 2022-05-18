package org.folio.search.configuration;

import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import org.folio.search.domain.dto.ResourceEvent;

/**
 * Responsible for configuration of kafka consumer bean factories and creation of topics at at application startup for
 * kafka listeners.
 */
@Log4j2
@Configuration
@RequiredArgsConstructor
public class KafkaConfiguration {

  private final KafkaProperties kafkaProperties;

  /**
   * Creates and configures {@link ConcurrentKafkaListenerContainerFactory} as Spring bean for consuming resource events
   * from Apache Kafka.
   *
   * @return {@link ConcurrentKafkaListenerContainerFactory} object as Spring bean.
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, ResourceEvent> kafkaListenerContainerFactory() {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, ResourceEvent>();
    factory.setBatchListener(true);
    factory.setConsumerFactory(jsonNodeConsumerFactory());
    return factory;
  }

  /**
   * Creates and configures {@link ConsumerFactory} as Spring bean.
   *
   * <p>Key type - {@link String}, value - {@link ResourceEvent}.</p>
   *
   * @return typed {@link ConsumerFactory} object as Spring bean.
   */
  @Bean
  public ConsumerFactory<String, ResourceEvent> jsonNodeConsumerFactory() {
    var deserializer = new JsonDeserializer<>(ResourceEvent.class);
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties());
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
  }

  /**
   * Creates and configures {@link ProducerFactory} as Spring bean.
   *
   * <p>Key type - {@link String}, value - {@link Object}.</p>
   *
   * @return typed {@link ProducerFactory} object as Spring bean.
   */
  @Bean
  public ProducerFactory<String, Object> producerFactory() {
    Map<String, Object> configProps = new HashMap<>(kafkaProperties.buildProducerProperties());
    configProps.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(configProps);
  }

  /**
   * Creates and configures {@link KafkaTemplate} as Spring bean.
   *
   * <p>Key type - {@link String}, value - {@link Object}.</p>
   *
   * @return typed {@link KafkaTemplate} object as Spring bean.
   */
  @Bean
  public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }
}
