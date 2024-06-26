package org.folio.search.service.consortium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.search.domain.dto.ConsortiumItemCollection;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.SearchService;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.builder.SearchSourceBuilder;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ConsortiumInstanceSearchServiceTest {

  private @Mock SearchService searchService;
  private @Mock SearchConfigurationProperties properties;
  private @Mock SearchRepository searchRepository;
  private @Mock ElasticsearchDocumentConverter documentConverter;
  private @InjectMocks ConsortiumInstanceSearchService service;

  @Test
  void getConsortiumHolding_positive() {
    var instanceId = UUID.randomUUID().toString();
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var holdings = List.of(holding(UUID.randomUUID().toString(), CENTRAL_TENANT_ID), holding(id, MEMBER_TENANT_ID));
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).holdings(holdings)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumHolding(id, request);

    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getTenantId()).isEqualTo(MEMBER_TENANT_ID);
    assertThat(result.getInstanceId()).isEqualTo(instanceId);
  }

  @Test
  void getConsortiumHolding_positive_noRecords() {
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var searchResult = SearchResult.of(0, Collections.<Instance>emptyList());

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumHolding(id, request);

    assertThat(result).isEqualTo(new ConsortiumHolding());
  }

  @Test
  void getConsortiumHolding_positive_noHoldings() {
    var instanceId = UUID.randomUUID().toString();
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var holdings = Collections.<Holding>emptyList();
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).holdings(holdings)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumHolding(id, request);

    assertThat(result).isEqualTo(new ConsortiumHolding());
  }

  @Test
  void getConsortiumHolding_positive_noDesiredHolding() {
    var instanceId = UUID.randomUUID().toString();
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var holdings = List.of(holding(UUID.randomUUID().toString(), CENTRAL_TENANT_ID));
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).holdings(holdings)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumHolding(id, request);

    assertThat(result).isEqualTo(new ConsortiumHolding());
  }

  @Test
  void getConsortiumItem_positive() {
    var instanceId = UUID.randomUUID().toString();
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var items = List.of(item(UUID.randomUUID().toString(), CENTRAL_TENANT_ID), item(id, MEMBER_TENANT_ID));
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).items(items)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumItem(id, request);

    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getTenantId()).isEqualTo(MEMBER_TENANT_ID);
    assertThat(result.getInstanceId()).isEqualTo(instanceId);
    assertThat(result.getHoldingsRecordId()).isNotBlank();
  }

  @Test
  void getConsortiumItem_positive_noRecords() {
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var searchResult = SearchResult.of(0, Collections.<Instance>emptyList());

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumItem(id, request);

    assertThat(result).isEqualTo(new ConsortiumItem());
  }

  @Test
  void getConsortiumItem_positive_noItems() {
    var instanceId = UUID.randomUUID().toString();
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var items = Collections.<Item>emptyList();
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).items(items)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumItem(id, request);

    assertThat(result).isEqualTo(new ConsortiumItem());
  }

  @Test
  void getConsortiumItem_positive_noDesiredItem() {
    var instanceId = UUID.randomUUID().toString();
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var items = List.of(item(UUID.randomUUID().toString(), CENTRAL_TENANT_ID));
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).items(items)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumItem(id, request);

    assertThat(result).isEqualTo(new ConsortiumItem());
  }

  @Test
  void fetchConsortiumBatchItems_positive() {
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    var instances = List.of(
      instanceForItems(List.of(item(UUID.randomUUID().toString(), CENTRAL_TENANT_ID),
        item(ids.get(0).toString(), MEMBER_TENANT_ID))),
      instanceForItems(List.of(item(ids.get(1).toString(), CENTRAL_TENANT_ID))),
      instanceForItems(List.of(item(UUID.randomUUID().toString(), CENTRAL_TENANT_ID))));
    var expectedConsortiumItems = instances.subList(0, instances.size() - 1).stream().map(instance -> {
      var item = instance.getItems().size() > 1 ? instance.getItems().get(1) : instance.getItems().get(0);
      return new ConsortiumItem()
        .id(item.getId())
        .instanceId(instance.getId())
        .tenantId(item.getTenantId())
        .holdingsRecordId(item.getHoldingsRecordId());
    }).toList();
    when(properties.getMaxSearchBatchRequestIdsCount()).thenReturn(20000L);
    when(searchRepository.search(any(CqlSearchRequest.class), any(SearchSourceBuilder.class)))
      .thenReturn(mock(SearchResponse.class));
    when(documentConverter.convertToSearchResult(any(SearchResponse.class), eq(Instance.class), any(BiFunction.class)))
      .thenReturn(SearchResult.of(2, expectedConsortiumItems));
    var expected = new ConsortiumItemCollection()
      .items(expectedConsortiumItems)
      .totalRecords(2);

    var result = service.fetchConsortiumBatchItems(CENTRAL_TENANT_ID, Sets.newHashSet(ids));

    assertThat(result).isEqualTo(expected);
  }

  private Holding holding(String id, String tenantId) {
    return new Holding()
      .id(id)
      .tenantId(tenantId);
  }

  private Item item(String id, String tenantId) {
    return new Item()
      .id(id)
      .tenantId(tenantId)
      .holdingsRecordId(UUID.randomUUID().toString());
  }

  private Instance instanceForHoldings(List<Holding> holdings) {
    return new Instance()
      .id(UUID.randomUUID().toString())
      .holdings(holdings);
  }

  private Instance instanceForItems(List<Item> items) {
    return new Instance()
      .id(UUID.randomUUID().toString())
      .items(items);
  }
}
