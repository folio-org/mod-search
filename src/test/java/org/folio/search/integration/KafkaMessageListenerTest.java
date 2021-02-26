package org.folio.search.integration;

import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.eventBody;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.service.IndexService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaMessageListenerTest {

  @InjectMocks private KafkaMessageListener messageListener;
  @Mock private IndexService indexService;
  @Mock private ResourceFetchService resourceFetchService;

  @Test
  void handleInstanceEvents() {
    var resourceEvents = List.of(eventBody(INSTANCE_RESOURCE, mapOf("id", randomId())));

    when(indexService.indexResources(resourceEvents)).thenReturn(getSuccessIndexOperationResponse());
    messageListener.handleInstanceEvents(resourceEvents);
    verify(indexService).indexResources(resourceEvents);
  }

  @Test
  void handleEvents() {
    var instanceId1 = randomId();
    var instanceId2 = randomId();
    var instanceId3 = randomId();
    var instanceBody1 = eventBody(null, mapOf("id", instanceId1, "title", "i1"));
    var instanceBody2 = eventBody(null, mapOf("id", instanceId2, "title", "i2"));
    var instanceBody3 = eventBody(null, mapOf("id", instanceId2, "title", "i3"));
    var itemBody = eventBody(null, mapOf("id", randomId(), "instanceId", instanceId2));
    var holdingBody1 = eventBody(null, mapOf("id", randomId(), "instanceId", instanceId3));
    var holdingBody2 = eventBody(null, mapOf("id", randomId(), "instanceId", null));

    var resourceIdEvents = List.of(
      ResourceIdEvent.of(instanceId1, INSTANCE_RESOURCE, TENANT_ID),
      ResourceIdEvent.of(instanceId2, INSTANCE_RESOURCE, TENANT_ID),
      ResourceIdEvent.of(instanceId3, INSTANCE_RESOURCE, TENANT_ID));

    var instanceEventBodies = List.of(instanceBody1, instanceBody2, instanceBody3);
    when(resourceFetchService.fetchInstancesByIds(resourceIdEvents)).thenReturn(instanceEventBodies);
    when(indexService.indexResources(instanceEventBodies)).thenReturn(getSuccessIndexOperationResponse());

    messageListener.handleEvents(List.of(
      new ConsumerRecord<>("inventory.instance", 0, 0, instanceId1, instanceBody1),
      new ConsumerRecord<>("inventory.instance", 0, 0, instanceId2, instanceBody2),
      new ConsumerRecord<>("inventory.item", 0, 0, instanceId2, itemBody),
      new ConsumerRecord<>("inventory.holding-record", 0, 0, instanceId3, holdingBody1),
      new ConsumerRecord<>("inventory.holding-record", 0, 0, null, holdingBody2)
    ));
  }
}
