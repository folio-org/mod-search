package org.folio.search.service.consortium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.lucene.search.TotalHits;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.converter.ConsortiumHoldingMapper;
import org.folio.search.converter.ConsortiumItemMapper;
import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.ConsortiumHoldingCollection;
import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.search.domain.dto.ConsortiumItemCollection;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.exception.RequestValidationException;
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
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
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
  void fetchConsortiumBatchHoldings_negative_exceedsAllowedIdsCount() {
    when(properties.getMaxSearchBatchRequestIdsCount()).thenReturn(10L);
    var ids = IntStream.range(1, 12).mapToObj(i -> UUID.randomUUID()).collect(Collectors.toSet());

    var ex =
      assertThrows(RequestValidationException.class,
        () -> service.fetchConsortiumBatchHoldings(CENTRAL_TENANT_ID, ids));

    assertThat(ex.getMessage()).isEqualTo("IDs array size exceeds the maximum allowed limit %s"
      .formatted(properties.getMaxSearchBatchRequestIdsCount()));
  }

  @Test
  void fetchConsortiumBatchHoldings_positive_notExceedsSearchResultWindow() {
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    var instancesHoldings = List.of(
      List.of(holding(UUID.randomUUID().toString(), CENTRAL_TENANT_ID),
        holding(ids.get(0).toString(), MEMBER_TENANT_ID)),
      List.of(holding(ids.get(1).toString(), CENTRAL_TENANT_ID)),
      List.of(holding(UUID.randomUUID().toString(), CENTRAL_TENANT_ID)));
    var expectedConsortiumHoldings = instancesHoldings.subList(0, instancesHoldings.size() - 1).stream()
      .map(holdings -> {
        var holding = holdings.size() > 1 ? holdings.get(1) : holdings.get(0);
        return ConsortiumHoldingMapper.toConsortiumHolding(UUID.randomUUID().toString(), holding);
      }).toList();
    when(properties.getMaxSearchBatchRequestIdsCount()).thenReturn(10L);
    when(searchRepository.search(any(CqlSearchRequest.class), any(SearchSourceBuilder.class)))
      .thenReturn(mock(SearchResponse.class));
    when(documentConverter.convertToSearchResult(any(SearchResponse.class), eq(Instance.class), any(BiFunction.class)))
      .thenReturn(SearchResult.of(2, List.of(expectedConsortiumHoldings)));
    var expected = new ConsortiumHoldingCollection()
      .holdings(expectedConsortiumHoldings)
      .totalRecords(2);

    var result = service.fetchConsortiumBatchHoldings(CENTRAL_TENANT_ID, Sets.newHashSet(ids));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void fetchConsortiumBatchHoldings_positive_exceedsSearchResultWindow() {
    var ids = IntStream.range(1, 10002).mapToObj(i -> UUID.randomUUID()).collect(Collectors.toList());
    var instancesHoldings = List.of(
      List.of(holding(UUID.randomUUID().toString(), CENTRAL_TENANT_ID),
        holding(ids.get(0).toString(), MEMBER_TENANT_ID)),
      List.of(holding(ids.get(1).toString(), CENTRAL_TENANT_ID)),
      List.of(holding(UUID.randomUUID().toString(), CENTRAL_TENANT_ID)));
    var expectedConsortiumHoldings = instancesHoldings.subList(0, instancesHoldings.size() - 1).stream()
      .map(holdings -> {
        var holding = holdings.size() > 1 ? holdings.get(1) : holdings.get(0);
        return ConsortiumHoldingMapper.toConsortiumHolding(UUID.randomUUID().toString(), holding);
      }).toList();
    var responseMock1 = mock(SearchResponse.class);
    var responseMock2 = mock(SearchResponse.class);
    var hit = mock(SearchHit.class);
    when(responseMock2.getHits()).thenReturn(SearchHits.empty());
    when(hit.getSortValues()).thenReturn(new Object[]{""});
    when(responseMock1.getHits()).thenReturn(
      new SearchHits(new SearchHit[]{hit}, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f));
    when(properties.getMaxSearchBatchRequestIdsCount()).thenReturn(20000L);
    when(properties.getSearchConsortiumRecordsPageSize()).thenReturn(2);
    when(searchRepository.search(any(CqlSearchRequest.class), any(SearchSourceBuilder.class)))
      .thenReturn(responseMock1).thenReturn(responseMock2);
    when(documentConverter.convertToSearchResult(eq(responseMock1), eq(Instance.class), any(BiFunction.class)))
      .thenReturn(SearchResult.of(2, List.of(expectedConsortiumHoldings)));
    var expected = new ConsortiumHoldingCollection()
      .holdings(expectedConsortiumHoldings)
      .totalRecords(2);

    var result = service.fetchConsortiumBatchHoldings(CENTRAL_TENANT_ID, Sets.newHashSet(ids));

    assertThat(result).isEqualTo(expected);
    verify(searchRepository, times(2))
      .search(any(CqlSearchRequest.class), any(SearchSourceBuilder.class));
    verify(documentConverter)
      .convertToSearchResult(any(SearchResponse.class), eq(Instance.class), any(BiFunction.class));
  }

  @Test
  void fetchConsortiumBatchItems_negative_exceedsAllowedIdsCount() {
    when(properties.getMaxSearchBatchRequestIdsCount()).thenReturn(10L);
    var ids = IntStream.range(1, 12).mapToObj(i -> UUID.randomUUID()).collect(Collectors.toSet());

    var ex =
      assertThrows(RequestValidationException.class, () -> service.fetchConsortiumBatchItems(CENTRAL_TENANT_ID, ids));

    assertThat(ex.getMessage()).isEqualTo("IDs array size exceeds the maximum allowed limit %s"
      .formatted(properties.getMaxSearchBatchRequestIdsCount()));
  }

  @Test
  void fetchConsortiumBatchItems_positive_notExceedsSearchResultWindow() {
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    var instancesItems = List.of(
      List.of(item(UUID.randomUUID().toString(), CENTRAL_TENANT_ID),
        item(ids.get(0).toString(), MEMBER_TENANT_ID)),
      List.of(item(ids.get(1).toString(), CENTRAL_TENANT_ID)),
      List.of(item(UUID.randomUUID().toString(), CENTRAL_TENANT_ID)));
    var expectedConsortiumItems = instancesItems.subList(0, instancesItems.size() - 1).stream()
      .map(items -> {
        var item = items.size() > 1 ? items.get(1) : items.get(0);
        return ConsortiumItemMapper.toConsortiumItem(UUID.randomUUID().toString(), item);
      }).toList();
    when(properties.getMaxSearchBatchRequestIdsCount()).thenReturn(10L);
    when(searchRepository.search(any(CqlSearchRequest.class), any(SearchSourceBuilder.class)))
      .thenReturn(mock(SearchResponse.class));
    when(documentConverter.convertToSearchResult(any(SearchResponse.class), eq(Instance.class), any(BiFunction.class)))
      .thenReturn(SearchResult.of(2, List.of(expectedConsortiumItems)));
    var expected = new ConsortiumItemCollection()
      .items(expectedConsortiumItems)
      .totalRecords(2);

    var result = service.fetchConsortiumBatchItems(CENTRAL_TENANT_ID, Sets.newHashSet(ids));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void fetchConsortiumBatchItems_positive_exceedsSearchResultWindow() {
    var ids = IntStream.range(1, 10002).mapToObj(i -> UUID.randomUUID()).collect(Collectors.toList());
    var instancesItems = List.of(
      List.of(item(UUID.randomUUID().toString(), CENTRAL_TENANT_ID),
        item(ids.get(0).toString(), MEMBER_TENANT_ID)),
      List.of(item(ids.get(1).toString(), CENTRAL_TENANT_ID)),
      List.of(item(UUID.randomUUID().toString(), CENTRAL_TENANT_ID)));
    var expectedConsortiumItems = instancesItems.subList(0, instancesItems.size() - 1).stream()
      .map(items -> {
        var item = items.size() > 1 ? items.get(1) : items.get(0);
        return ConsortiumItemMapper.toConsortiumItem(UUID.randomUUID().toString(), item);
      }).toList();
    var responseMock1 = mock(SearchResponse.class);
    var responseMock2 = mock(SearchResponse.class);
    var hit = mock(SearchHit.class);
    when(responseMock2.getHits()).thenReturn(SearchHits.empty());
    when(hit.getSortValues()).thenReturn(new Object[]{""});
    when(responseMock1.getHits()).thenReturn(
      new SearchHits(new SearchHit[]{hit}, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f));
    when(properties.getMaxSearchBatchRequestIdsCount()).thenReturn(20000L);
    when(properties.getSearchConsortiumRecordsPageSize()).thenReturn(2);
    when(searchRepository.search(any(CqlSearchRequest.class), any(SearchSourceBuilder.class)))
      .thenReturn(responseMock1).thenReturn(responseMock2);
    when(documentConverter.convertToSearchResult(eq(responseMock1), eq(Instance.class), any(BiFunction.class)))
      .thenReturn(SearchResult.of(2, List.of(expectedConsortiumItems)));
    var expected = new ConsortiumItemCollection()
      .items(expectedConsortiumItems)
      .totalRecords(2);

    var result = service.fetchConsortiumBatchItems(CENTRAL_TENANT_ID, Sets.newHashSet(ids));

    assertThat(result).isEqualTo(expected);
    verify(searchRepository, times(2))
      .search(any(CqlSearchRequest.class), any(SearchSourceBuilder.class));
    verify(documentConverter)
      .convertToSearchResult(any(SearchResponse.class), eq(Instance.class), any(BiFunction.class));
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
}
