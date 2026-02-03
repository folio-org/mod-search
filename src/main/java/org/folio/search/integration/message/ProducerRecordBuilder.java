package org.folio.search.integration.message;

import static org.folio.spring.tools.kafka.FolioKafkaProperties.TENANT_ID;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.folio.spring.integration.XOkapiHeaders;

/**
 * Builder for creating Kafka producer records with proper header management.
 */
public class ProducerRecordBuilder<K, V> {

  private final String topic;
  private final K key;
  private final V value;
  private final Headers headers;

  public ProducerRecordBuilder(String topic, K key, V value, Headers headers) {
    this.topic = topic;
    this.key = key;
    this.value = value;
    this.headers = headers;
  }

  /**
   * Updates tenant-related headers and builds the producer record.
   *
   * @param targetTenant the target tenant ID
   * @return a new ProducerRecord with updated headers
   */
  public ProducerRecord<K, V> withUpdatedTenantHeaders(String targetTenant) {
    var targetTenantBytes = targetTenant.getBytes(StandardCharsets.UTF_8);

    var producerRecord = new ProducerRecord<>(topic, key, value);
    copyHeaders(headers, producerRecord.headers(), targetTenantBytes);
    
    return producerRecord;
  }

  private void copyHeaders(Headers source, Headers destination, byte[] targetTenantBytes) {
    source.forEach(header -> {
      var headerKey = header.key();
      if (isTenantHeader(headerKey)) {
        destination.add(headerKey, targetTenantBytes);
      } else {
        destination.add(headerKey, header.value());
      }
    });
  }

  private boolean isTenantHeader(String key) {
    return TENANT_ID.equals(key) || XOkapiHeaders.TENANT.equals(key);
  }
}
