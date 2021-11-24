package org.folio.search.integration;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.client.cql.CqlQuery.exactMatchAny;
import static org.folio.search.model.service.ResultList.asSinglePage;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.JsonConverter.MAP_TYPE_REFERENCE;
import static org.folio.search.utils.JsonUtils.LIST_OF_MAPS_TYPE_REFERENCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.Callable;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.integration.inventory.InventoryViewClient;
import org.folio.search.integration.inventory.InventoryViewClient.InstanceView;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.service.TenantScopedExecutionService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ResourceFetchServiceTest {

  @InjectMocks private ResourceFetchService resourceFetchService;
  @Mock private TenantScopedExecutionService executionService;
  @Mock private InventoryViewClient inventoryClient;

  @Test
  void fetchInstancesByIdPositive() {
    var events = resourceIdEvents();
    var instanceId1 = events.get(0).getId();
    var instanceId2 = events.get(1).getId();

    var instance1 = instanceView(new Instance().id(instanceId1).title("instance1"));
    var instance2 = instanceView(new Instance().id(instanceId2).title("instance2")
      .holdings(List.of(new Holding().id("holdingId"))).items(List.of(new Item().id("itemId"))));

    when(inventoryClient.getInstances(exactMatchAny("id", List.of(instanceId1, instanceId2)), 2))
      .thenReturn(asSinglePage(List.of(instance1, instance2)));
    when(executionService.executeTenantScoped(any(), any())).thenAnswer(inv -> inv.<Callable<?>>getArgument(1).call());

    var actual = resourceFetchService.fetchInstancesByIds(events);

    assertThat(actual).isEqualTo(List.of(
      resourceEvent(null, INSTANCE_RESOURCE, mapOf("id", instanceId1, "title", "instance1")),
      resourceEvent(null, INSTANCE_RESOURCE, mapOf("id", instanceId2, "title", "instance2",
        "holdings", List.of(mapOf("id", "holdingId")), "items", List.of(mapOf("id", "itemId"))))));
  }

  @Test
  void fetchInstancesByIds_positive_emptyListOfIds() {
    var actual = resourceFetchService.fetchInstancesByIds(emptyList());
    assertThat(actual).isEmpty();
  }

  private static List<ResourceIdEvent> resourceIdEvents() {
    return List.of(
      ResourceIdEvent.of(randomId(), INSTANCE_RESOURCE, TENANT_ID, INDEX),
      ResourceIdEvent.of(randomId(), INSTANCE_RESOURCE, TENANT_ID, INDEX),
      ResourceIdEvent.of(randomId(), RESOURCE_NAME, TENANT_ID, INDEX));
  }

  private static InstanceView instanceView(Instance instance) {
    var instanceMap = OBJECT_MAPPER.convertValue(instance, MAP_TYPE_REFERENCE);
    var itemsMap = OBJECT_MAPPER.convertValue(instance.getItems(), LIST_OF_MAPS_TYPE_REFERENCE);
    var holdingsMap = OBJECT_MAPPER.convertValue(instance.getHoldings(), LIST_OF_MAPS_TYPE_REFERENCE);
    return new InstanceView(instanceMap, holdingsMap, itemsMap);
  }
}
