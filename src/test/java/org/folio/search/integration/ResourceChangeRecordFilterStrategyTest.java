package org.folio.search.integration;

import static org.folio.search.model.types.ResourceType.AUTHORITY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.ResourceDeleteEventSubType;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.integration.message.interceptor.ResourceChangeRecordFilterStrategy;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ResourceChangeRecordFilterStrategyTest {

  private final ResourceChangeRecordFilterStrategy filterStrategy = new ResourceChangeRecordFilterStrategy();

  @Mock
  private ConsumerRecord<String, ResourceEvent> consumerRecord;

  @Test
  void shouldNotFilterResourceEventWithoutName() {
    var event = createResourceEvent(ResourceEventType.DELETE, ResourceDeleteEventSubType.HARD_DELETE, null);
    mockConsumerRecord(event);

    var actual = filterStrategy.filter(consumerRecord);

    assertFalse(actual);
  }

  @ParameterizedTest
  @EnumSource(value = ResourceType.class, names = {"INSTANCE", "INSTANCE_CONTRIBUTOR", "INSTANCE_SUBJECT"})
  void shouldNotFilterNonAuthResourceEvent(ResourceType resourceName) {
    var event = createResourceEvent(ResourceEventType.DELETE, ResourceDeleteEventSubType.HARD_DELETE, resourceName);
    mockConsumerRecord(event);

    var actual = filterStrategy.filter(consumerRecord);

    assertFalse(actual);
  }

  @Test
  void shouldFilterHardDeleteAuthResourceEvent() {
    var event = createResourceEvent(ResourceEventType.DELETE, ResourceDeleteEventSubType.HARD_DELETE, AUTHORITY);
    mockConsumerRecord(event);
    var actual = filterStrategy.filter(consumerRecord);

    assertTrue(actual);
  }

  @Test
  void shouldNotFilterSoftDeleteAuthResourceEvent() {
    var event = createResourceEvent(ResourceEventType.DELETE, ResourceDeleteEventSubType.SOFT_DELETE, AUTHORITY);
    mockConsumerRecord(event);

    var actual = filterStrategy.filter(consumerRecord);

    assertFalse(actual);
  }

  @Test
  void shouldNotFilterNonDeleteAuthResourceEvent() {
    var event = createResourceEvent(ResourceEventType.CREATE, ResourceDeleteEventSubType.HARD_DELETE, AUTHORITY);
    mockConsumerRecord(event);

    var actual = filterStrategy.filter(consumerRecord);

    assertFalse(actual);
  }

  private void mockConsumerRecord(ResourceEvent event) {
    when(consumerRecord.value()).thenReturn(event);
  }

  private ResourceEvent createResourceEvent(ResourceEventType eventType, ResourceDeleteEventSubType subType,
                                            ResourceType resource) {
    var event = new ResourceEvent();
    event.setId("1");
    event.setType(eventType);
    event.setDeleteEventSubType(subType);
    event.setResourceName(resource == null ? null : resource.getName());
    return event;
  }
}
