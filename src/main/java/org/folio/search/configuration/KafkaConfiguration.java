package org.folio.search.configuration;

import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.search.configuration.properties.FolioKafkaProperties;
import org.folio.search.domain.dto.ResourceEventBody;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.retry.support.RetryTemplate;

/**
 * Responsible for configuration of kafka consumer bean factories and creation of topics at at application startup for
 * kafka listeners.
 */
@Log4j2
@Configuration
@RequiredArgsConstructor
public class KafkaConfiguration {

  public static final String KAFKA_RETRY_TEMPLATE_NAME = "kafkaMessageListenerRetryTemplate";
  private final KafkaProperties kafkaProperties;
  private final FolioKafkaProperties folioKafkaProperties;

  /**
   * Creates and configures {@link ConcurrentKafkaListenerContainerFactory} as Spring bean for consuming resource events
   * from Apache Kafka.
   *
   * @return {@link ConcurrentKafkaListenerContainerFactory} object as Spring bean.
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, ResourceEventBody> kafkaListenerContainerFactory() {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, ResourceEventBody>();
    factory.setBatchListener(true);
    factory.setConsumerFactory(jsonNodeConsumerFactory());
    return factory;
  }

  /**
   * Constructs a batch handler that tries to deliver messages 10 times with configured interval, if exception is not
   * resolved than messages will be redelivered by next poll() call. Creates retry template to consume messages from
   * kafka.
   *
   * @return created {@link RetryTemplate} object
   */
  @Bean(name = KAFKA_RETRY_TEMPLATE_NAME)
  public RetryTemplate kafkaMessageListenerRetryTemplate() {
    return RetryTemplate.builder()
      .maxAttempts((int) folioKafkaProperties.getRetryDeliveryAttempts())
      .fixedBackoff(folioKafkaProperties.getRetryIntervalMs())
      .build();
  }

  /**
   * Creates and configures {@link ConsumerFactory} as Spring bean.
   *
   * <p>Key type - {@link String}, value - {@link JsonNode}.</p>
   *
   * @return typed {@link ConsumerFactory} object as Spring bean.
   */
  private ConsumerFactory<String, ResourceEventBody> jsonNodeConsumerFactory() {
    var deserializer = new JsonDeserializer<>(ResourceEventBody.class);
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties());
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
  }
}
