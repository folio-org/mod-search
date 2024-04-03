package org.folio.search.configuration;

import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;
import static org.folio.search.configuration.KafkaConfiguration.SearchTopic.CONSORTIUM_INSTANCE;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.integration.interceptor.CompositeRecordFilterStrategy;
import org.folio.search.model.event.ConsortiumInstanceEvent;
import org.folio.spring.config.properties.FolioEnvironment;
import org.folio.spring.tools.kafka.FolioKafkaTopic;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.kafka.listener.CompositeBatchInterceptor;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Responsible for configuration of kafka consumer bean factories and creation of topics at application startup for
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
  public ConcurrentKafkaListenerContainerFactory<String, ResourceEvent> standardListenerContainerFactory(
    RecordFilterStrategy<String, ResourceEvent>[] recordFilterStrategies,
    BatchInterceptor<String, ResourceEvent>[] batchInterceptors) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, ResourceEvent>();
    factory.setBatchListener(true);
    factory.setConsumerFactory(resourceEventConsumerFactory());
    factory.setRecordFilterStrategy(new CompositeRecordFilterStrategy<>(recordFilterStrategies));
    factory.setBatchInterceptor(new CompositeBatchInterceptor<>(batchInterceptors));
    return factory;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, ConsortiumInstanceEvent> consortiumListenerContainerFactory() {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, ConsortiumInstanceEvent>();
    factory.setBatchListener(true);
    factory.setConsumerFactory(consortiumEventConsumerFactory());
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
  public ConsumerFactory<String, ResourceEvent> resourceEventConsumerFactory() {
    var deserializer = new JsonDeserializer<>(ResourceEvent.class, false);
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
  }

  @Bean
  public ConsumerFactory<String, ConsortiumInstanceEvent> consortiumEventConsumerFactory() {
    var deserializer = new JsonDeserializer<>(ConsortiumInstanceEvent.class);
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
  }

  /**
   * Creates and configures {@link ProducerFactory} as Spring bean.
   *
   * <p>Key type - {@link String}, value - {@link ResourceEvent}.</p>
   *
   * @return typed {@link ProducerFactory} object as Spring bean.
   */
  @Bean
  public ProducerFactory<String, ResourceEvent> producerFactory() {
    return getProducerFactory();
  }

  /**
   * Creates and configures {@link KafkaTemplate} as Spring bean.
   *
   * <p>Key type - {@link String}, value - {@link ResourceEvent}.</p>
   *
   * @return typed {@link KafkaTemplate} object as Spring bean.
   */
  @Bean
  public KafkaTemplate<String, ResourceEvent> kafkaTemplate(ProducerFactory<String, ResourceEvent> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  @Bean
  public ProducerFactory<String, ConsortiumInstanceEvent> consortiumProducerFactory() {
    return getProducerFactory();
  }

  @Bean
  public KafkaTemplate<String, ConsortiumInstanceEvent> consortiumKafkaTemplate(
    ProducerFactory<String, ConsortiumInstanceEvent> consortiumProducerFactory) {
    return new KafkaTemplate<>(consortiumProducerFactory);
  }

  @Bean
  public FolioMessageProducer<ConsortiumInstanceEvent> consortiumInstanceMessageProducer(
    KafkaTemplate<String, ConsortiumInstanceEvent> consortiumKafkaTemplate) {
    var producer = new FolioMessageProducer<>(consortiumKafkaTemplate, CONSORTIUM_INSTANCE);
    producer.setKeyMapper(ConsortiumInstanceEvent::getInstanceId);
    return producer;
  }

  private <T> ProducerFactory<String, T> getProducerFactory() {
    Map<String, Object> configProps = new HashMap<>(kafkaProperties.buildProducerProperties(null));
    configProps.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(configProps);
  }

  enum SearchTopic implements FolioKafkaTopic {
    CONSORTIUM_INSTANCE("search.consortium.instance");

    private final String topicName;

    SearchTopic(String topicName) {
      this.topicName = topicName;
    }

    @Override
    public String topicName() {
      return topicName;
    }

    @Override
    public String envId() {
      return FolioEnvironment.getFolioEnvName();
    }
  }
}
