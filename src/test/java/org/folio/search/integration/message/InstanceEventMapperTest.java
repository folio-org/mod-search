package org.folio.search.integration.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.folio.search.configuration.kafka.KafkaConfiguration;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.type.UnitTest;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class InstanceEventMapperTest {

  private static final String TENANT_ID = "test-tenant";
  private static final String CENTRAL_TENANT_ID = "central-tenant";
  private static final String INSTANCE_ID = randomId();
  private static final String INSTANCE_TOPIC = "folio.test-tenant.inventory.instance";
  private static final String ITEM_TOPIC = "folio.test-tenant.inventory.item";

  @Mock
  private ConsortiumTenantService consortiumTenantService;

  @InjectMocks
  private InstanceEventMapper mapper;

  @BeforeEach
  void setUp() {
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.empty());
  }

  @Test
  void mapToProducerRecord_shouldMapInstanceCreateEvent() {
    var resourceEvent = resourceEvent(null, ResourceType.INSTANCE, CREATE,
      mapOf("id", INSTANCE_ID, "title", "Test Instance"), null);
    resourceEvent.tenant(TENANT_ID);

    var consumerRecord = createConsumerRecord(INSTANCE_ID, resourceEvent, INSTANCE_TOPIC);

    var result = mapper.mapToProducerRecords(consumerRecord);

    assertThat(result).isNotNull().hasSize(1);
    assertThat(result.getFirst().key()).isEqualTo(INSTANCE_ID);
    assertThat(result.getFirst().value()).isNotNull();
    assertThat(result.getFirst().value().instanceId()).isEqualTo(INSTANCE_ID);
    assertThat(result.getFirst().value().tenant()).isEqualTo(TENANT_ID);
    assertThat(result.getFirst().topic()).isEqualTo(
      KafkaConfiguration.SearchTopic.INDEX_INSTANCE.fullTopicName(TENANT_ID));
  }

  @Test
  void mapToProducerRecord_shouldMapInstanceUpdateEvent() {
    var resourceEvent = resourceEvent(null, ResourceType.INSTANCE, UPDATE,
      mapOf("id", INSTANCE_ID, "title", "Updated Title"),
      mapOf("id", INSTANCE_ID, "title", "Old Title"));
    resourceEvent.tenant(TENANT_ID);

    var consumerRecord = createConsumerRecord(INSTANCE_ID, resourceEvent, INSTANCE_TOPIC);

    var result = mapper.mapToProducerRecords(consumerRecord);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().value().instanceId()).isEqualTo(INSTANCE_ID);
    assertThat(result.getFirst().value().tenant()).isEqualTo(TENANT_ID);
  }

  @Test
  void mapToProducerRecord_shouldMapInstanceDeleteEvent() {
    var resourceEvent = resourceEvent(null, ResourceType.INSTANCE, DELETE,
      null, mapOf("id", INSTANCE_ID));
    resourceEvent.tenant(TENANT_ID);

    var consumerRecord = createConsumerRecord(INSTANCE_ID, resourceEvent, INSTANCE_TOPIC);

    var result = mapper.mapToProducerRecords(consumerRecord);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().value().instanceId()).isEqualTo(INSTANCE_ID);
    assertThat(result.getFirst().value().tenant()).isEqualTo(TENANT_ID);
  }

  @Test
  void mapToProducerRecord_shouldMapItemDeleteEvent() {
    var itemId = randomId();
    var resourceEvent = resourceEvent(null, ResourceType.ITEM, DELETE,
      null, mapOf("id", itemId, "instanceId", INSTANCE_ID));
    resourceEvent.tenant(TENANT_ID);

    var consumerRecord = createConsumerRecord(itemId, resourceEvent, ITEM_TOPIC);

    var result = mapper.mapToProducerRecords(consumerRecord);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().value().instanceId()).isEqualTo(INSTANCE_ID);
    assertThat(result.getFirst().value().tenant()).isEqualTo(TENANT_ID);
  }

  @Test
  void mapToProducerRecords_shouldExtractInstanceIdFromItemEvent() {
    var itemId = randomId();
    var resourceEvent = resourceEvent(null, ResourceType.ITEM, CREATE,
      mapOf("id", itemId, "instanceId", INSTANCE_ID), null);
    resourceEvent.tenant(TENANT_ID);

    var consumerRecord = createConsumerRecord(itemId, resourceEvent, ITEM_TOPIC);

    var result = mapper.mapToProducerRecords(consumerRecord);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().value().instanceId()).isEqualTo(INSTANCE_ID);
    assertThat(result.getFirst().value().tenant()).isEqualTo(TENANT_ID);
  }

  @Test
  void mapToProducerRecords_shouldExtractInstanceIdFromHoldingEvent() {
    var holdingId = randomId();
    var holdingTopic = "folio.test-tenant.inventory.holding";
    var resourceEvent = resourceEvent(null, ResourceType.HOLDINGS, CREATE,
      mapOf("id", holdingId, "instanceId", INSTANCE_ID), null);
    resourceEvent.tenant(TENANT_ID);

    var consumerRecord = createConsumerRecord(holdingId, resourceEvent, holdingTopic);

    var result = mapper.mapToProducerRecords(consumerRecord);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().value().instanceId()).isEqualTo(INSTANCE_ID);
    assertThat(result.getFirst().value().tenant()).isEqualTo(TENANT_ID);
  }

  @Test
  void mapToProducerRecords_shouldUseCentralTenantWhenAvailable() {
    when(consortiumTenantService.getCentralTenant(TENANT_ID))
      .thenReturn(Optional.of(CENTRAL_TENANT_ID));

    var resourceEvent = resourceEvent(null, ResourceType.INSTANCE, CREATE,
      mapOf("id", INSTANCE_ID), null);
    resourceEvent.tenant(TENANT_ID);

    var consumerRecord = createConsumerRecord(INSTANCE_ID, resourceEvent, INSTANCE_TOPIC);

    var result = mapper.mapToProducerRecords(consumerRecord);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().value().tenant()).isEqualTo(CENTRAL_TENANT_ID);
    assertThat(result.getFirst().topic()).contains(CENTRAL_TENANT_ID);
  }

  @Test
  void mapToProducerRecord_shouldCopyHeadersToProducerRecords() {
    var resourceEvent = resourceEvent(null, ResourceType.INSTANCE, CREATE,
      mapOf("id", INSTANCE_ID), null);
    resourceEvent.tenant(TENANT_ID);

    var headers = new RecordHeaders();
    headers.add("X-Custom-Header", "custom-value".getBytes(StandardCharsets.UTF_8));
    headers.add(XOkapiHeaders.URL, "http://okapi:9130".getBytes(StandardCharsets.UTF_8));

    var consumerRecord = new ConsumerRecord<>(
      INSTANCE_TOPIC, 0, 0, INSTANCE_ID, resourceEvent);
    consumerRecord.headers().add("X-Custom-Header", "custom-value".getBytes(StandardCharsets.UTF_8));
    consumerRecord.headers().add(XOkapiHeaders.URL, "http://okapi:9130".getBytes(StandardCharsets.UTF_8));

    var result = mapper.mapToProducerRecords(consumerRecord);

    assertThat(result.getFirst().headers()).isNotNull();
    var headerKeys = new java.util.ArrayList<String>();
    result.getFirst().headers().forEach(header -> headerKeys.add(header.key()));
    assertThat(headerKeys).contains(XOkapiHeaders.URL);
  }

  @Test
  void mapToProducerRecord_shouldUpdateTenantHeadersInProducerRecords() {
    var resourceEvent = resourceEvent(null, ResourceType.INSTANCE, CREATE,
      mapOf("id", INSTANCE_ID), null);
    resourceEvent.tenant(TENANT_ID);

    var consumerRecord = createConsumerRecord(INSTANCE_ID, resourceEvent, INSTANCE_TOPIC);
    consumerRecord.headers().add(FolioKafkaProperties.TENANT_ID,
      "old-tenant".getBytes(StandardCharsets.UTF_8));
    consumerRecord.headers().add(XOkapiHeaders.TENANT,
      "old-tenant".getBytes(StandardCharsets.UTF_8));

    var result = mapper.mapToProducerRecords(consumerRecord);

    var tenantIdHeader = result.getFirst().headers().lastHeader(FolioKafkaProperties.TENANT_ID);
    var okapiTenantHeader = result.getFirst().headers().lastHeader(XOkapiHeaders.TENANT);

    assertThat(tenantIdHeader).isNotNull();
    assertThat(new String(tenantIdHeader.value(), StandardCharsets.UTF_8)).isEqualTo(TENANT_ID);
    assertThat(okapiTenantHeader).isNotNull();
    assertThat(new String(okapiTenantHeader.value(), StandardCharsets.UTF_8))
      .isEqualTo(TENANT_ID);
  }

  @Test
  void mapToProducerRecords_shouldHandleNullPayload() {
    // For DELETE events or REINDEX, the ID comes from the key, not from payload
    var resourceEvent = resourceEvent(null, ResourceType.INSTANCE, DELETE, null, null);
    resourceEvent.tenant(TENANT_ID);

    var consumerRecord = createConsumerRecord(INSTANCE_ID, resourceEvent, INSTANCE_TOPIC);

    var result = mapper.mapToProducerRecords(consumerRecord);

    assertThat(result).isEmpty();
  }

  @Test
  void mapToProducerRecords_shouldHandleBoundWithEvent() {
    var boundWithId = randomId();
    var boundWithTopic = "folio.test-tenant.inventory.bound-with";
    var resourceEvent = resourceEvent(null, ResourceType.BOUND_WITH, CREATE,
      mapOf("id", boundWithId, "instanceId", INSTANCE_ID), null);
    resourceEvent.tenant(TENANT_ID);

    var consumerRecord = createConsumerRecord(boundWithId, resourceEvent, boundWithTopic);

    var result = mapper.mapToProducerRecords(consumerRecord);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().value().instanceId()).isEqualTo(INSTANCE_ID);
  }

  @Test
  void mapToProducerRecords_shouldGenerateCorrectTopicName() {
    var resourceEvent = resourceEvent(null, ResourceType.INSTANCE, CREATE,
      mapOf("id", INSTANCE_ID), null);
    resourceEvent.tenant(TENANT_ID);

    var consumerRecord = createConsumerRecord(INSTANCE_ID, resourceEvent, INSTANCE_TOPIC);

    var result = mapper.mapToProducerRecords(consumerRecord);

    assertThat(result.getFirst().topic())
      .isEqualTo(KafkaConfiguration.SearchTopic.INDEX_INSTANCE.fullTopicName(TENANT_ID));
  }

  @Test
  void mapToProducerRecords_shouldCreateTwoRecordsWhenInstanceIdChanges() {
    var itemId = randomId();
    var oldInstanceId = randomId();
    var newInstanceId = randomId();
    var resourceEvent = resourceEvent(null, ResourceType.ITEM, UPDATE,
      mapOf("id", itemId, "instanceId", newInstanceId),
      mapOf("id", itemId, "instanceId", oldInstanceId));
    resourceEvent.tenant(TENANT_ID);

    var consumerRecord = createConsumerRecord(itemId, resourceEvent, ITEM_TOPIC);

    var result = mapper.mapToProducerRecords(consumerRecord);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).value().instanceId()).isEqualTo(oldInstanceId);
    assertThat(result.get(0).value().tenant()).isEqualTo(TENANT_ID);
    assertThat(result.get(1).value().instanceId()).isEqualTo(newInstanceId);
    assertThat(result.get(1).value().tenant()).isEqualTo(TENANT_ID);
  }

  private ConsumerRecord<String, ResourceEvent> createConsumerRecord(
    String key, ResourceEvent value, String topic) {
    return new ConsumerRecord<>(topic, 0, 0, key, value);
  }
}
