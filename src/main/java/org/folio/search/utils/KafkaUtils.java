package org.folio.search.utils;

import static org.folio.spring.config.properties.FolioEnvironment.getFolioEnvName;

import lombok.experimental.UtilityClass;
import org.apache.kafka.clients.consumer.ConsumerRecord;

@UtilityClass
public class KafkaUtils {

  /**
   * Returns topic name in the format - `{env}.{tenant}.{topic-name}`
   *
   * @param initialName initial topic name as {@link String}
   * @param tenantId    tenant id as {@link String}
   * @return topic name as {@link String} object
   */
  public static String getTenantTopicName(String initialName, String tenantId) {
    return org.folio.spring.tools.kafka.KafkaUtils.getTenantTopicName(initialName, getFolioEnvName(), tenantId);
  }

  public static String getTopicName(ConsumerRecord<?, ?> consumerRecord) {
    var topic = consumerRecord.topic();
    return topic.substring(topic.indexOf('.', topic.indexOf('.') + 1) + 1);
  }
}
