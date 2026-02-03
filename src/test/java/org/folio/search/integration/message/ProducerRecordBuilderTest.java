package org.folio.search.integration.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.spring.tools.kafka.FolioKafkaProperties.TENANT_ID;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.folio.search.model.event.IndexInstanceEvent;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ProducerRecordBuilderTest {

  private static final String TOPIC = "test-topic";
  private static final String KEY = "test-key";
  private static final String ORIGINAL_TENANT = "original-tenant";
  private static final String TARGET_TENANT = "target-tenant";
  private static final String CUSTOM_HEADER = "X-Custom-Header";
  private static final String CUSTOM_VALUE = "custom-value";

  @Test
  void withUpdatedTenantHeaders_shouldUpdateTenantHeaders() {
    var headers = new RecordHeaders();
    headers.add(TENANT_ID, ORIGINAL_TENANT.getBytes(StandardCharsets.UTF_8));
    headers.add(XOkapiHeaders.TENANT, ORIGINAL_TENANT.getBytes(StandardCharsets.UTF_8));
    headers.add(CUSTOM_HEADER, CUSTOM_VALUE.getBytes(StandardCharsets.UTF_8));

    var event = new IndexInstanceEvent(TARGET_TENANT, "instance-id");
    var builder = new ProducerRecordBuilder<>(TOPIC, KEY, event, headers);

    var result = builder.withUpdatedTenantHeaders(TARGET_TENANT);

    assertThat(result).isNotNull();
    assertThat(result.topic()).isEqualTo(TOPIC);
    assertThat(result.key()).isEqualTo(KEY);
    assertThat(result.value()).isEqualTo(event);

    var resultHeaders = getHeadersAsMap(result);
    assertThat(resultHeaders)
      .containsEntry(TENANT_ID, TARGET_TENANT)
      .containsEntry(XOkapiHeaders.TENANT, TARGET_TENANT)
      .containsEntry(CUSTOM_HEADER, CUSTOM_VALUE);
  }

  @Test
  void withUpdatedTenantHeaders_shouldHandleEmptyHeaders() {
    var headers = new RecordHeaders();
    var event = new IndexInstanceEvent(TARGET_TENANT, "instance-id");
    var builder = new ProducerRecordBuilder<>(TOPIC, KEY, event, headers);

    var result = builder.withUpdatedTenantHeaders(TARGET_TENANT);

    assertThat(result).isNotNull();
    // With empty input headers, output should also be empty (no tenant headers added automatically)
    assertThat(result.headers()).isEmpty();
  }

  @Test
  void withUpdatedTenantHeaders_shouldOverwriteExistingTenantHeaders() {
    var headers = new RecordHeaders();
    headers.add(TENANT_ID, ORIGINAL_TENANT.getBytes(StandardCharsets.UTF_8));
    headers.add(XOkapiHeaders.TENANT, ORIGINAL_TENANT.getBytes(StandardCharsets.UTF_8));

    var event = new IndexInstanceEvent(TARGET_TENANT, "instance-id");
    var builder = new ProducerRecordBuilder<>(TOPIC, KEY, event, headers);

    var result = builder.withUpdatedTenantHeaders(TARGET_TENANT);

    var resultHeaders = getHeadersAsMap(result);
    assertThat(resultHeaders)
      .containsEntry(TENANT_ID, TARGET_TENANT)
      .containsEntry(XOkapiHeaders.TENANT, TARGET_TENANT);

    // Verify no duplicate headers
    var tenantIdCount = countHeaders(result, TENANT_ID);
    var okapiTenantCount = countHeaders(result, XOkapiHeaders.TENANT);
    assertThat(tenantIdCount).isEqualTo(1);
    assertThat(okapiTenantCount).isEqualTo(1);
  }

  @Test
  void withUpdatedTenantHeaders_shouldPreserveNonTenantHeaders() {
    var headers = new RecordHeaders();
    headers.add("X-Header-1", "value1".getBytes(StandardCharsets.UTF_8));
    headers.add("X-Header-2", "value2".getBytes(StandardCharsets.UTF_8));
    headers.add(TENANT_ID, ORIGINAL_TENANT.getBytes(StandardCharsets.UTF_8));

    var event = new IndexInstanceEvent(TARGET_TENANT, "instance-id");
    var builder = new ProducerRecordBuilder<>(TOPIC, KEY, event, headers);

    var result = builder.withUpdatedTenantHeaders(TARGET_TENANT);

    var resultHeaders = getHeadersAsMap(result);
    assertThat(resultHeaders)
      .containsEntry("X-Header-1", "value1")
      .containsEntry("X-Header-2", "value2")
      .containsEntry(TENANT_ID, TARGET_TENANT);
  }

  @Test
  void withUpdatedTenantHeaders_shouldHandleMultipleCustomHeaders() {
    var headers = new RecordHeaders();
    for (int i = 0; i < 5; i++) {
      headers.add("X-Custom-" + i, ("value-" + i).getBytes(StandardCharsets.UTF_8));
    }
    headers.add(TENANT_ID, ORIGINAL_TENANT.getBytes(StandardCharsets.UTF_8));
    headers.add(XOkapiHeaders.TENANT, ORIGINAL_TENANT.getBytes(StandardCharsets.UTF_8));

    var event = new IndexInstanceEvent(TARGET_TENANT, "instance-id");
    var builder = new ProducerRecordBuilder<>(TOPIC, KEY, event, headers);

    var result = builder.withUpdatedTenantHeaders(TARGET_TENANT);

    assertThat(result.headers()).hasSize(7); // 5 custom + 2 tenant headers
  }

  @Test
  void withUpdatedTenantHeaders_shouldHandleNullByteArrays() {
    var headers = new RecordHeaders();
    headers.add("X-Null-Header", null);
    headers.add(TENANT_ID, ORIGINAL_TENANT.getBytes(StandardCharsets.UTF_8));

    var event = new IndexInstanceEvent(TARGET_TENANT, "instance-id");
    var builder = new ProducerRecordBuilder<>(TOPIC, KEY, event, headers);

    var result = builder.withUpdatedTenantHeaders(TARGET_TENANT);

    assertThat(result).isNotNull();
    var resultHeaders = getHeadersAsMap(result);
    assertThat(resultHeaders).containsEntry(TENANT_ID, TARGET_TENANT);
  }

  private java.util.Map<String, String> getHeadersAsMap(ProducerRecord<?, ?> producerRecord) {
    var map = new java.util.HashMap<String, String>();
    producerRecord.headers().forEach(header -> {
      if (header.value() != null) {
        map.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
      }
    });
    return map;
  }

  private long countHeaders(ProducerRecord<?, ?> producerRecord, String headerKey) {
    var count = 0;
    for (Header header : producerRecord.headers()) {
      if (header.key().equals(headerKey)) {
        count++;
      }
    }
    return count;
  }
}
