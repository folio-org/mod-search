package org.folio.support.utils;

import static org.folio.support.utils.JsonTestUtils.asJsonString;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Assertions;

@Log4j2
@UtilityClass
public class KafkaTestUtils {
  public static final String FOLIO_ENV_VAL = "reindex-listener-it";
  public static final String FOLIO_ENV = "folio.environment=" + FOLIO_ENV_VAL;

  public static void sendMessage(Object value, String topic,
                                 KafkaProducer<String, String> producer) {
    sendMessage(null, value, topic, producer);
  }

  public static void sendMessage(String key, Object value, String topic,
                                 KafkaProducer<String, String> producer) {
    ProducerRecord<String, String> producerRecord = key == null
                                                    ? new ProducerRecord<>(topic, asJsonString(value))
                                                    : new ProducerRecord<>(topic, key, asJsonString(value));
    producer.send(producerRecord,
      (metadata, exception) -> {
        if (exception != null) {
          Assertions.fail("Error sending record", exception);
        }
        log.info("Message sent to topic {}", metadata.topic());
      });
  }
}
