package org.folio.search.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.service.ResultList.asSinglePage;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.eventBody;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.search.domain.dto.Instance;
import org.folio.search.integration.inventory.InventoryClient;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ResourceFetchServiceTest {

  @InjectMocks private ResourceFetchService resourceFetchService;
  @Spy private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);
  @Mock private InventoryClient inventoryClient;

  @Test
  void fetchInstancesByIdPositive() {
    var events = resourceIdEvents();
    var instance1 = new Instance().id(events.get(0).getId()).title("instance1");
    var instance2 = new Instance().id(events.get(1).getId()).title("instance2");

    when(inventoryClient.getInstances(List.of(instance1.getId(), instance2.getId()))).thenReturn(
      asSinglePage(List.of(instance1, instance2)));

    var actual = resourceFetchService.fetchInstancesByIds(events);

    assertThat(actual).isEqualTo(List.of(
      eventBody(INSTANCE_RESOURCE, mapOf("id", instance1.getId(), "title", "instance1")),
      eventBody(INSTANCE_RESOURCE, mapOf("id", instance2.getId(), "title", "instance2"))));

    verify(jsonConverter).convert(eq(instance1), any());
    verify(jsonConverter).convert(eq(instance2), any());
  }

  private static List<ResourceIdEvent> resourceIdEvents() {
    return List.of(
      ResourceIdEvent.of(randomId(), INSTANCE_RESOURCE, TENANT_ID),
      ResourceIdEvent.of(randomId(), INSTANCE_RESOURCE, TENANT_ID),
      ResourceIdEvent.of(randomId(), RESOURCE_NAME, TENANT_ID));
  }
}
