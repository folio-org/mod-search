package org.folio.search.service.consortium;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.JsonConverter.MAP_TYPE_REFERENCE;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.TestUtils.randomId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ConsortiumInstanceServiceTest {

  private static final String CENTRAL_TENANT = "consortium";
  private static final String[] CONSORTIUM_TENANTS = new String[] {"consortium1", "consortium2", CENTRAL_TENANT};
  private static final String[] NORMAL_TENANTS = new String[] {"tenant1", "tenant2"};

  private final ObjectMapper mapper = new ObjectMapper();

  private @Mock JsonConverter jsonConverter;
  private @Mock ConsortiumInstanceRepository repository;
  private @Mock ConsortiaTenantExecutor consortiaTenantExecutor;
  private @Mock ConsortiaTenantService consortiaTenantService;
  private @InjectMocks ConsortiumInstanceService service;

  private @Captor ArgumentCaptor<List<ConsortiumInstance>> instancesCaptor;
  private @Captor ArgumentCaptor<Set<ConsortiumInstanceId>> instanceIdsCaptor;

  {
    mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
  }

  @BeforeEach
  void setUp() {
    lenient().when(jsonConverter.fromJsonToMap(any())).thenAnswer(
      invocationOnMock -> mapper.readValue(invocationOnMock.getArgument(0).toString(), MAP_TYPE_REFERENCE));
    lenient().when(jsonConverter.toJson(any())).thenAnswer(
      invocationOnMock -> mapper.writeValueAsString(invocationOnMock.getArgument(0)));

    for (String normalTenant : NORMAL_TENANTS) {
      lenient().when(consortiaTenantService.getCentralTenant(normalTenant)).thenReturn(Optional.empty());
    }

    for (String consortiumTenant : CONSORTIUM_TENANTS) {
      lenient().when(consortiaTenantService.getCentralTenant(consortiumTenant)).thenReturn(Optional.of(CENTRAL_TENANT));
    }

    lenient().doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(consortiaTenantExecutor).run(any());
    lenient().doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get())
      .when(consortiaTenantExecutor).execute(any());
  }

  @Test
  void saveInstances_positive_shouldReturnInstancesThatAreNotConsortium() {
    var resourceEvents =
      List.of(resourceEvent(NORMAL_TENANTS, 0), resourceEvent(NORMAL_TENANTS, 1));
    var actual = service.saveInstances(resourceEvents);

    assertThat(actual).isEqualTo(resourceEvents);
    verify(repository, never()).save(any());
  }

  @Test
  void saveInstances_positive_shouldProcessConsortiumInstances() {
    var resourceEvents =
      List.of(resourceEvent(CONSORTIUM_TENANTS, 0),
        resourceEvent(CONSORTIUM_TENANTS, 1),
        resourceEvent(CONSORTIUM_TENANTS, 2));
    var actual = service.saveInstances(resourceEvents);

    assertThat(actual).isNullOrEmpty();

    verify(repository).save(instancesCaptor.capture());
    assertThat(instancesCaptor.getValue())
      .hasSize(3)
      .allMatch(instance -> asList(CONSORTIUM_TENANTS).contains(instance.id().tenantId()))
      .map(instance -> mapper.readValue(instance.instance(), Instance.class))
      .allMatch(instance -> asList(CONSORTIUM_TENANTS).contains(instance.getTenantId()),
        "tenant populated to instance")
      .allMatch(instance -> instance.getHoldings()
          .stream()
          .allMatch(holding -> asList(CONSORTIUM_TENANTS).contains(holding.getTenantId())),
        "tenant populated to holdings")
      .allMatch(instance -> instance.getItems()
          .stream()
          .allMatch(item -> asList(CONSORTIUM_TENANTS).contains(item.getTenantId())),
        "tenant populated to items")
      .anyMatch(instance -> instance.getTenantId().equals(CENTRAL_TENANT) && instance.getShared());
  }

  @Test
  void deleteInstances_positive_shouldReturnInstancesThatAreNotConsortium() {
    var resourceEvents =
      List.of(resourceEvent(NORMAL_TENANTS, 0), resourceEvent(NORMAL_TENANTS, 1));
    var actual = service.deleteInstances(resourceEvents);

    assertThat(actual).isEqualTo(resourceEvents);
    verify(repository, never()).delete(any());
  }

  @Test
  void deleteInstances_positive_shouldProcessConsortiumInstances() {
    var resourceEvents =
      List.of(resourceEvent(CONSORTIUM_TENANTS, 0),
        resourceEvent(CONSORTIUM_TENANTS, 1),
        resourceEvent(CONSORTIUM_TENANTS, 2));
    var actual = service.deleteInstances(resourceEvents);

    assertThat(actual).isNullOrEmpty();

    verify(repository).delete(instanceIdsCaptor.capture());
    assertThat(instanceIdsCaptor.getValue())
      .hasSize(3)
      .containsAll(resourceEvents.stream().map(x -> new ConsortiumInstanceId(x.getTenant(), x.getId())).toList());
  }

  @Test
  void fetchInstances_positive_shouldMergeInstancesById() {
    var instanceIds = List.of(randomId(), randomId());
    when(repository.fetch(instanceIds)).thenReturn(List.of(
      consortiumInstance(CONSORTIUM_TENANTS[0], instanceIds.get(0), true),
      consortiumInstance(CONSORTIUM_TENANTS[1], instanceIds.get(0), true),
      consortiumInstance(CONSORTIUM_TENANTS[2], instanceIds.get(0), false),
      consortiumInstance(CONSORTIUM_TENANTS[2], instanceIds.get(1), false)
    ));

    var actual = service.fetchInstances(instanceIds);

    assertThat(actual).hasSize(2)
      .allMatch(resourceEvent -> instanceIds.contains(resourceEvent.getId()))
      .allMatch(resourceEvent -> resourceEvent.getTenant().equals(CENTRAL_TENANT));
    for (ResourceEvent resourceEvent : actual) {
      if (resourceEvent.getId().equals(instanceIds.get(0))) {
        // merged instance with holdings and items from member tenants
        assertThat(resourceEvent.getNew()).isNotNull();
        assertThat(getNewAsMap(resourceEvent))
          .hasEntrySatisfying("holdings", o -> assertThat(castToList(o)).hasSize(2))
          .hasEntrySatisfying("items", o -> assertThat(castToList(o)).hasSize(4));
      } else {
        // merged instance that has no holdings and items in member tenants
        assertThat(resourceEvent.getNew()).isNotNull();
        assertThat(getNewAsMap(resourceEvent))
          .hasEntrySatisfying("holdings", o -> assertThat(castToList(o)).isNullOrEmpty())
          .hasEntrySatisfying("items", o -> assertThat(castToList(o)).isNullOrEmpty());
      }
    }
  }

  @SuppressWarnings("unchecked")
  private List<Object> castToList(Object o) {
    return (List<Object>) o;
  }

  private ResourceEvent resourceEvent(String[] tenants, int x) {
    var id = randomId();
    return new ResourceEvent().tenant(tenants[x])
      .id(id)
      ._new(mapper.convertValue(new Instance().id(id)
        .items(List.of(new Item().id(randomId())))
        .holdings(List.of(new Holding().id(randomId()))), MAP_TYPE_REFERENCE));
  }

  @SneakyThrows
  private ConsortiumInstance consortiumInstance(String tenant, String id, boolean withItemsHoldings) {
    var consortiumInstanceId = new ConsortiumInstanceId(tenant, id);
    var instance = new Instance().id(id).tenantId(tenant);
    if (withItemsHoldings) {
      instance.items(List.of(new Item().id(randomId()).tenantId(tenant),
          new Item().id(randomId()).tenantId(tenant)))
        .holdings(List.of(new Holding().id(randomId()).tenantId(tenant)));
    }
    return new ConsortiumInstance(consortiumInstanceId, mapper.writeValueAsString(instance));
  }

}
