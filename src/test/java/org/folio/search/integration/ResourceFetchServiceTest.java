package org.folio.search.integration;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.model.client.CqlQuery.exactMatchAny;
import static org.folio.search.model.service.ResultList.asSinglePage;
import static org.folio.search.utils.JsonConverter.MAP_TYPE_REFERENCE;
import static org.folio.search.utils.JsonUtils.LIST_OF_MAPS_TYPE_REFERENCE;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.client.InventoryViewClient;
import org.folio.search.client.InventoryViewClient.InstanceView;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.client.CqlQueryParam;
import org.folio.search.model.service.ResultList;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@Disabled //TODO fix
@UnitTest
@ExtendWith(MockitoExtension.class)
class ResourceFetchServiceTest {

  @InjectMocks
  private ResourceFetchService resourceFetchService;
  @Mock
  private InventoryViewClient inventoryClient;
  @Mock
  private FolioExecutionContext context;

  @BeforeEach
  void setUp() {
    lenient().when(context.getTenantId()).thenReturn(TENANT_ID);
  }

  @Test
  void fetchInstancesById_positive() {
    var events = resourceEvents();
    var instanceId1 = events.get(0).getId();
    var instanceId2 = events.get(1).getId();

    var instance1 = instanceView(new Instance().id(instanceId1).title("inst1"), null);
    var instance2 = instanceView(new Instance().id(instanceId2).title("inst2")
      .holdings(List.of(new Holding().id("holdingId"))).items(List.of(new Item().id("itemId"))), true);

    when(inventoryClient.getInstances(exactMatchAny(CqlQueryParam.ID, List.of(instanceId1, instanceId2)), 2))
      .thenReturn(asSinglePage(List.of(instance1, instance2)));

    var actual = resourceFetchService.fetchInstancesByIds(events);

    assertThat(cleanUp(actual)).isEqualTo(List.of(
      resourceEvent(instanceId1, ResourceType.INSTANCE,
        mapOf("id", instanceId1, "title", "inst1", "isBoundWith", null)),
      resourceEvent(instanceId2, ResourceType.INSTANCE, UPDATE,
        mapOf("id", instanceId2, "title", "inst2",
          "holdings", List.of(mapOf("id", "holdingId")),
          "items", List.of(mapOf("id", "itemId")), "isBoundWith", true),
        mapOf("id", instanceId2, "title", "old"))
    ));
    verify(inventoryClient, times(1)).getInstances(any(), anyInt());
  }

  @Test
  void fetchInstancesById_negative_oneOfResourcesByIdIsNotFound() {
    var events = resourceEvents();
    var instanceId1 = events.get(0).getId();
    var instanceId2 = events.get(1).getId();
    var instanceView = instanceView(new Instance().id(instanceId1).title("inst1"), null);

    when(inventoryClient.getInstances(exactMatchAny(CqlQueryParam.ID, List.of(instanceId1, instanceId2)), 2))
      .thenReturn(asSinglePage(List.of(instanceView)));

    var actual = resourceFetchService.fetchInstancesByIds(events);
    assertThat(cleanUp(actual)).isEqualTo(List.of(resourceEvent(instanceId1, ResourceType.INSTANCE,
      mapOf("id", instanceId1, "title", "inst1", "isBoundWith", null))));
  }

  @Test
  void fetchInstancesById_negative_resourceReturnedWithInvalidId() {
    var id = randomId();
    var resourceEvent = resourceEvent(id, ResourceType.INSTANCE, UPDATE,
      mapOf("id", id, "title", "new"), mapOf("id", id, "title", "old"));
    var invalidId = randomId();
    var instanceView = instanceView(new Instance().id(invalidId).title("inst1"), null);

    when(inventoryClient.getInstances(exactMatchAny(CqlQueryParam.ID, List.of(id)), 1))
      .thenReturn(asSinglePage(List.of(instanceView)));

    var actual = resourceFetchService.fetchInstancesByIds(List.of(resourceEvent));
    assertThat(cleanUp(actual)).isEqualTo(List.of(
      resourceEvent(invalidId, ResourceType.INSTANCE,
        mapOf("id", invalidId, "title", "inst1", "isBoundWith", null))
    ));
  }

  @Test
  void fetchInstancesByIds_positive_emptyListOfIds() {
    var actual = resourceFetchService.fetchInstancesByIds(emptyList());
    assertThat(actual).isEmpty();
  }

  @Test
  void fetchInstancesByIds_positive_withMoreThan50Resources() {
    var events = generateResourceEvents();
    var firstPartInstances = events.stream()
      .limit(50)
      .map(resourceEvent -> instanceView(new Instance().id(resourceEvent.getId()).title("test"), null))
      .toList();
    var secondPartInstances = List.of(instanceView(new Instance()
      .id(events.get(50).getId()).title("inst2").holdings(List.of(new Holding().id("holdingId")))
      .items(List.of(new Item().id("itemId"))), true));

    when(inventoryClient.getInstances(any(), anyInt()))
      .thenAnswer(new Answer<>() {
        private int count = 0;

        public ResultList<InstanceView> answer(InvocationOnMock invocation) {
          if (count++ == 1) {
            // it will be returned the first time the method is called
            return asSinglePage(firstPartInstances);
          }
          // it will be returned the second time the method is called
          return asSinglePage(secondPartInstances);
        }
      });

    var actual = resourceFetchService.fetchInstancesByIds(events);

    assertThat(actual).hasSize(51);
    verify(inventoryClient, times(2)).getInstances(any(), anyInt());
  }

  private List<ResourceEvent> cleanUp(List<ResourceEvent> actual) {
    for (ResourceEvent event : actual) {
      if (event.getNew() instanceof Map<?, ?> map) {
        cleanMap(map);
        if (map.get("items") instanceof List<?> list) {
          for (Object o : list) {
            if (o instanceof Map<?, ?> itemMap) {
              cleanMap(itemMap);
            }
          }
        }
        if (map.get("holdings") instanceof List<?> list) {
          for (Object o : list) {
            if (o instanceof Map<?, ?> holdingMap) {
              cleanMap(holdingMap);
            }
          }
        }
      }
      if (event.getOld() instanceof Map<?, ?> map) {
        map.entrySet().removeIf(entry -> entry.getValue() instanceof List<?> list && list.isEmpty());
      }
    }
    return actual;
  }

  private void cleanMap(Map<?, ?> itemMap) {
    itemMap.entrySet().removeIf(entry -> entry.getValue() instanceof List<?> itemList && itemList.isEmpty());
  }

  private static List<ResourceEvent> resourceEvents() {
    var updateEventId = randomId();
    return List.of(
      resourceEvent(randomId(), ResourceType.INSTANCE, CREATE),
      resourceEvent(updateEventId, ResourceType.INSTANCE, UPDATE,
        mapOf("id", updateEventId, "title", "new"),
        mapOf("id", updateEventId, "title", "old")));
  }

  private static InstanceView instanceView(Instance instance, Boolean isBoundWith) {
    var instanceMap = OBJECT_MAPPER.convertValue(instance, MAP_TYPE_REFERENCE);
    var itemsMap = OBJECT_MAPPER.convertValue(instance.getItems(), LIST_OF_MAPS_TYPE_REFERENCE);
    var holdingsMap = OBJECT_MAPPER.convertValue(instance.getHoldings(), LIST_OF_MAPS_TYPE_REFERENCE);
    return new InstanceView(instanceMap, holdingsMap, itemsMap, isBoundWith);
  }

  private static List<ResourceEvent> generateResourceEvents() {
    return Stream.generate(() -> resourceEvent(randomId(), ResourceType.INSTANCE, CREATE))
      .limit(51)
      .toList();
  }
}
