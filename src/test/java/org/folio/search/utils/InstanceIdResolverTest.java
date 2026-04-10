package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class InstanceIdResolverTest {

  @Test
  void resolve_reindexEvent_usesRecordKey() {
    var event = new ResourceEvent().type(ResourceEventType.REINDEX);
    var record = new ConsumerRecord<>("test.inventory.instance", 0, 0L, "instance-key-123", event);

    assertThat(InstanceIdResolver.resolve(record)).isEqualTo("instance-key-123");
  }

  @Test
  void resolve_instanceEvent_usesIdField() {
    var payload = Map.<String, Object>of("id", "inst-001", "title", "Test Title");
    var event = new ResourceEvent().type(ResourceEventType.CREATE)._new(payload);
    var record = new ConsumerRecord<>("env.tenant.inventory.instance", 0, 0L, "key", event);

    assertThat(InstanceIdResolver.resolve(record)).isEqualTo("inst-001");
  }

  @Test
  void resolve_holdingEvent_usesInstanceIdField() {
    var payload = Map.<String, Object>of("id", "hold-001", "instanceId", "inst-002");
    var event = new ResourceEvent().type(ResourceEventType.CREATE)._new(payload);
    var record = new ConsumerRecord<>("env.tenant.inventory.holdings-record", 0, 0L, "key", event);

    assertThat(InstanceIdResolver.resolve(record)).isEqualTo("inst-002");
  }

  @Test
  void resolve_itemEvent_usesInstanceIdField() {
    var payload = Map.<String, Object>of("id", "item-001", "instanceId", "inst-003");
    var event = new ResourceEvent().type(ResourceEventType.CREATE)._new(payload);
    var record = new ConsumerRecord<>("env.tenant.inventory.item", 0, 0L, "key", event);

    assertThat(InstanceIdResolver.resolve(record)).isEqualTo("inst-003");
  }

  @Test
  void isInstanceResource_trueForInstanceTopic() {
    var record = new ConsumerRecord<String, ResourceEvent>(
      "env.tenant.inventory.instance", 0, 0L, "key", new ResourceEvent());
    assertThat(InstanceIdResolver.isInstanceResource(record)).isTrue();
  }

  @Test
  void isInstanceResource_falseForHoldingsTopic() {
    var record = new ConsumerRecord<String, ResourceEvent>(
      "env.tenant.inventory.holdings-record", 0, 0L, "key", new ResourceEvent());
    assertThat(InstanceIdResolver.isInstanceResource(record)).isFalse();
  }

  @Test
  void isInstanceResource_falseForItemTopic() {
    var record = new ConsumerRecord<String, ResourceEvent>(
      "env.tenant.inventory.item", 0, 0L, "key", new ResourceEvent());
    assertThat(InstanceIdResolver.isInstanceResource(record)).isFalse();
  }
}
