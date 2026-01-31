package org.folio.search.integration;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.randomId;
import static org.folio.support.utils.TestUtils.resourceEvent;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.event.IndexInstanceEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.reindex.InstanceFetchService;
import org.folio.search.service.reindex.jdbc.UploadInstanceRepository;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.folio.support.utils.JsonTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class InstanceFetchServiceTest {

  @InjectMocks
  private InstanceFetchService resourceFetchService;
  @Mock
  private UploadInstanceRepository instanceRepository;
  @Mock
  private FolioExecutionContext context;

  @BeforeEach
  void setUp() {
    resourceFetchService = new InstanceFetchService(context, instanceRepository);
    lenient().when(context.getTenantId()).thenReturn(TENANT_ID);
  }

  @Test
  void fetchInstancesById_positive() {
    var instanceId1 = randomId();
    var instanceId2 = randomId();
    var events = List.of(
      new IndexInstanceEvent(TENANT_ID, instanceId1),
      new IndexInstanceEvent(TENANT_ID, instanceId2)
    );

    var instance1 = instanceMap(new Instance().id(instanceId1).title("inst1").isBoundWith(null));
    var instance2 = instanceMap(new Instance().id(instanceId2).title("inst2").isBoundWith(true)
      .holdings(List.of(new Holding().id("holdingId"))).items(List.of(new Item().id("itemId"))));

    when(instanceRepository.fetchByIds(Set.of(instanceId1, instanceId2)))
      .thenReturn(List.of(instance1, instance2));

    var actual = resourceFetchService.fetchInstancesByIds(events);

    assertThat(cleanUp(actual)).containsExactlyInAnyOrder(
      resourceEvent(instanceId1, ResourceType.INSTANCE, CREATE,
        mapOf("id", instanceId1, "title", "inst1"), null),
      resourceEvent(instanceId2, ResourceType.INSTANCE, CREATE,
        mapOf("id", instanceId2, "title", "inst2",
          "isBoundWith", true,
          "items", List.of(mapOf("id", "itemId")),
          "holdings", List.of(mapOf("id", "holdingId"))),
        null)
    );
    verify(instanceRepository, times(1)).fetchByIds(any());
  }

  @Test
  void fetchInstancesById_negative_oneOfResourcesByIdIsNotFound() {
    var instanceId1 = randomId();
    var instanceId2 = randomId();
    var events = List.of(
      new IndexInstanceEvent(TENANT_ID, instanceId1),
      new IndexInstanceEvent(TENANT_ID, instanceId2)
    );
    var instanceMap = instanceMap(new Instance().id(instanceId1).title("inst1").isBoundWith(null));

    when(instanceRepository.fetchByIds(Set.of(instanceId1, instanceId2)))
      .thenReturn(List.of(instanceMap));

    var actual = resourceFetchService.fetchInstancesByIds(events);
    assertThat(cleanUp(actual)).containsExactlyInAnyOrder(
      resourceEvent(instanceId1, ResourceType.INSTANCE, CREATE,
        mapOf("id", instanceId1, "title", "inst1"), null),
      resourceEvent(instanceId2, ResourceType.INSTANCE, org.folio.search.domain.dto.ResourceEventType.DELETE, null, null)
    );
  }

  @Test
  void fetchInstancesById_negative_resourceReturnedWithInvalidId() {
    var id = randomId();
    var event = new IndexInstanceEvent(TENANT_ID, id);
    var invalidId = randomId();
    var instanceMap = instanceMap(new Instance().id(invalidId).title("inst1").isBoundWith(null));

    when(instanceRepository.fetchByIds(Set.of(id)))
      .thenReturn(List.of(instanceMap));

    var actual = resourceFetchService.fetchInstancesByIds(List.of(event));
    assertThat(cleanUp(actual)).containsExactlyInAnyOrder(
      resourceEvent(invalidId, ResourceType.INSTANCE, CREATE,
        mapOf("id", invalidId, "title", "inst1"), null),
      resourceEvent(id, ResourceType.INSTANCE, org.folio.search.domain.dto.ResourceEventType.DELETE, null, null)
    );
  }

  @Test
  void fetchInstancesByIds_positive_emptyListOfIds() {
    var actual = resourceFetchService.fetchInstancesByIds(emptyList());
    assertThat(actual).isEmpty();
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

  private static Map<String, Object> instanceMap(Instance instance) {
    return JsonTestUtils.toMap(instance);
  }
}
