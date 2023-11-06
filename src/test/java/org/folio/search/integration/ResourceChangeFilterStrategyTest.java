package org.folio.search.integration;

import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventSubType;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
public class ResourceChangeFilterStrategyTest {

  private final ResourceChangeFilterStrategy filterStrategy = new ResourceChangeFilterStrategy();

  @Mock
  private ConsumerRecord<String, ResourceEvent> consumerRecord;

  @Test
  void shouldNotFilterResourceEventWithoutName() {
    var event = createResourceEvent(ResourceEventSubType.HARD_DELETE, null);
    mockConsumerRecord(event);

    var actual = filterStrategy.filter(consumerRecord);

    assertFalse(actual);
  }

  @ValueSource(strings = {"instance", "contributor", "instance_subject"})
  @ParameterizedTest
  void shouldNotFilterNonAuthResourceEvent(String resourceName) {
    var event = createResourceEvent(ResourceEventSubType.HARD_DELETE, resourceName);
    mockConsumerRecord(event);

    var actual = filterStrategy.filter(consumerRecord);

    assertFalse(actual);
  }

  @Test
  void shouldFilterHardDeleteAuthResourceEvent() {
    var event = createResourceEvent(ResourceEventSubType.HARD_DELETE, AUTHORITY_RESOURCE);
    mockConsumerRecord(event);
    var actual = filterStrategy.filter(consumerRecord);

    assertTrue(actual);
  }

  @Test
  void shouldNotFilterSoftDeleteAuthResourceEvent() {
    var event = createResourceEvent(ResourceEventSubType.SOFT_DELETE, AUTHORITY_RESOURCE);
    mockConsumerRecord(event);

    var actual = filterStrategy.filter(consumerRecord);

    assertFalse(actual);
  }

  private void mockConsumerRecord(ResourceEvent event) {
    when(consumerRecord.value()).thenReturn(event);
  }

  private ResourceEvent createResourceEvent(ResourceEventSubType subType, String resourceName) {
    var event = new ResourceEvent();
    event.setId("1");
    event.setSubType(subType);
    event.setResourceName(resourceName);
    return event;
  }
}
