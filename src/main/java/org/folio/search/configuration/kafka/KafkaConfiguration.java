package org.folio.search.configuration.kafka;

import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.folio.spring.config.properties.FolioEnvironment;
import org.folio.spring.tools.kafka.FolioKafkaTopic;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Responsible for configuration of kafka consumer bean factories and creation of topics at application startup for
 * kafka listeners.
 */
@Log4j2
public abstract class KafkaConfiguration {

  protected static <T> ProducerFactory<String, T> getProducerFactory(KafkaProperties kafkaProperties) {
    Map<String, Object> configProps = new HashMap<>(kafkaProperties.buildProducerProperties(null));
    configProps.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(configProps);
  }

  protected static <T> DefaultKafkaConsumerFactory<String, T> getConsumerFactory(JsonDeserializer<T> deserializer,
                                                                                 KafkaProperties kafkaProperties) {
    return getConsumerFactory(deserializer, kafkaProperties, Collections.emptyMap());
  }

  protected static <T> DefaultKafkaConsumerFactory<String, T> getConsumerFactory(JsonDeserializer<T> deserializer,
                                                                                 KafkaProperties kafkaProperties,
                                                                                 Map<String, Object> overrideProps) {
    var config = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    config.putAll(overrideProps);
    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
  }

  public enum SearchTopic implements FolioKafkaTopic {
    CONSORTIUM_INSTANCE("search.consortium.instance"),
    REINDEX_RANGE_INDEX("search.reindex.range-index");

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
