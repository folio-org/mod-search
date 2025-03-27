package org.folio.search.service.consortium;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.converter.ConsortiumHoldingMapper.toConsortiumHolding;
import static org.folio.search.converter.ConsortiumItemMapper.toConsortiumItem;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.lucene.search.TotalHits;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.BatchIdsDto.IdentifierTypeEnum;
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
    var instanceId = randomUUID().toString();
    var id = randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var holdings = List.of(holding(randomUUID().toString(), CENTRAL_TENANT_ID), holding(id, MEMBER_TENANT_ID));
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).holdings(holdings)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumHolding(id, request);

    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getTenantId()).isEqualTo(MEMBER_TENANT_ID);
    assertThat(result.getInstanceId()).isEqualTo(instanceId);
  }

  @Test
  void getConsortiumHolding_positive_noRecords() {
    var id = randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var searchResult = SearchResult.of(0, Collections.<Instance>emptyList());

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumHolding(id, request);

    assertThat(result).isEqualTo(new ConsortiumHolding());
  }

  @Test
  void getConsortiumHolding_positive_noHoldings() {
    var instanceId = randomUUID().toString();
    var id = randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var holdings = Collections.<Holding>emptyList();
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).holdings(holdings)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumHolding(id, request);

    assertThat(result).isEqualTo(new ConsortiumHolding());
  }

  @Test
  void getConsortiumHolding_positive_noDesiredHolding() {
    var instanceId = randomUUID().toString();
    var id = randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var holdings = List.of(holding(randomUUID().toString(), CENTRAL_TENANT_ID));
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).holdings(holdings)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumHolding(id, request);

    assertThat(result).isEqualTo(new ConsortiumHolding());
  }

  @Test
  void getConsortiumItem_positive() {
    var instanceId = randomUUID().toString();
    var id = randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var items = List.of(item(randomUUID().toString(), CENTRAL_TENANT_ID), item(id, MEMBER_TENANT_ID));
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
    var id = randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var searchResult = SearchResult.of(0, Collections.<Instance>emptyList());

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumItem(id, request);

    assertThat(result).isEqualTo(new ConsortiumItem());
  }

  @Test
  void getConsortiumItem_positive_noItems() {
    var instanceId = randomUUID().toString();
    var id = randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var items = Collections.<Item>emptyList();
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).items(items)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumItem(id, request);

    assertThat(result).isEqualTo(new ConsortiumItem());
  }

  @Test
  void getConsortiumItem_positive_noDesiredItem() {
    var instanceId = randomUUID().toString();
    var id = randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var items = List.of(item(randomUUID().toString(), CENTRAL_TENANT_ID));
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).items(items)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumItem(id, request);

    assertThat(result).isEqualTo(new ConsortiumItem());
  }

  @Test
  void fetchConsortiumBatchHoldings_negative_exceedsAllowedIdsCount() {
    when(properties.getMaxSearchBatchRequestIdsCount()).thenReturn(10L);
    var ids = IntStream.range(1, 12).mapToObj(i -> randomUUID().toString()).collect(Collectors.toSet());

    var ex =
      assertThrows(RequestValidationException.class,
        () -> service.fetchConsortiumBatchHoldings(CENTRAL_TENANT_ID, ids, IdentifierTypeEnum.ID));

    assertThat(ex.getMessage()).isEqualTo("IDs array size exceeds the maximum allowed limit %s"
      .formatted(properties.getMaxSearchBatchRequestIdsCount()));
  }

  @Test
  void fetchConsortiumBatchHoldings_positive_notExceedsSearchResultWindow() {
    var ids = List.of(randomUUID().toString(), randomUUID().toString());
    var expectedConsortiumHoldings = List.of(
      toConsortiumHolding(randomUUID().toString(), holding(ids.get(0), MEMBER_TENANT_ID)),
      toConsortiumHolding(randomUUID().toString(), holding(ids.get(1), CENTRAL_TENANT_ID))
    );
    when(properties.getMaxSearchBatchRequestIdsCount()).thenReturn(10L);
    when(searchRepository.search(any(CqlSearchRequest.class), any(SearchSourceBuilder.class)))
      .thenReturn(mock(SearchResponse.class));
    when(documentConverter.convertToSearchResult(any(SearchResponse.class), eq(Instance.class), any()))
      .thenReturn(SearchResult.of(2, List.of(expectedConsortiumHoldings)));
    var expected = new ConsortiumHoldingCollection()
      .holdings(expectedConsortiumHoldings)
      .totalRecords(2);

    var result = service.fetchConsortiumBatchHoldings(CENTRAL_TENANT_ID, new HashSet<>(ids), IdentifierTypeEnum.ID);

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void fetchConsortiumBatchHoldings_positive_exceedsSearchResultWindow() {
    var ids = IntStream.range(1, 10002).mapToObj(i -> randomUUID().toString()).toList();
    var expectedHolding1 = toConsortiumHolding(randomUUID().toString(), holding(ids.get(0), MEMBER_TENANT_ID));
    var expectedHolding2 = toConsortiumHolding(randomUUID().toString(), holding(ids.get(1), CENTRAL_TENANT_ID));
    var responseMock1 = mock(SearchResponse.class);
    var responseMock2 = mock(SearchResponse.class);
    var responseMock3 = mock(SearchResponse.class);
    var hit = mock(SearchHit.class);
    when(hit.getSortValues()).thenReturn(new Object[]{""});
    when(responseMock2.getHits()).thenReturn(
      new SearchHits(new SearchHit[]{hit}, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f));
    when(responseMock1.getHits()).thenReturn(
      new SearchHits(new SearchHit[]{hit}, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f));
    when(responseMock3.getHits()).thenReturn(SearchHits.empty());
    when(properties.getMaxSearchBatchRequestIdsCount()).thenReturn(20000L);
    when(properties.getSearchConsortiumRecordsPageSize()).thenReturn(1);
    when(searchRepository.search(any(CqlSearchRequest.class), any(SearchSourceBuilder.class)))
      .thenReturn(responseMock1).thenReturn(responseMock2).thenReturn(responseMock3);
    when(documentConverter.convertToSearchResult(eq(responseMock1), eq(Instance.class), any()))
      .thenReturn(SearchResult.of(1, List.of(List.of(expectedHolding1))));
    when(documentConverter.convertToSearchResult(eq(responseMock2), eq(Instance.class), any()))
      .thenReturn(SearchResult.of(1, List.of(List.of(expectedHolding2))));
    var expected = new ConsortiumHoldingCollection()
      .holdings(List.of(expectedHolding1, expectedHolding2))
      .totalRecords(2);

    var result = service.fetchConsortiumBatchHoldings(CENTRAL_TENANT_ID, Sets.newHashSet(ids), IdentifierTypeEnum.ID);

    assertThat(result).isEqualTo(expected);
    verify(searchRepository, times(3)).search(any(CqlSearchRequest.class), any(SearchSourceBuilder.class));
    verify(documentConverter, times(2))
      .convertToSearchResult(any(SearchResponse.class), eq(Instance.class), any());
  }

  @Test
  void fetchConsortiumBatchItems_negative_exceedsAllowedIdsCount() {
    when(properties.getMaxSearchBatchRequestIdsCount()).thenReturn(10L);
    var ids = IntStream.range(1, 12).mapToObj(i -> randomUUID().toString()).collect(Collectors.toSet());

    var ex = assertThrows(RequestValidationException.class,
        () -> service.fetchConsortiumBatchItems(CENTRAL_TENANT_ID, ids, IdentifierTypeEnum.ID));

    assertThat(ex.getMessage()).isEqualTo("IDs array size exceeds the maximum allowed limit %s"
      .formatted(properties.getMaxSearchBatchRequestIdsCount()));
  }

  @Test
  void fetchConsortiumBatchItems_positive_notExceedsSearchResultWindow() {
    var ids = List.of(randomUUID().toString(), randomUUID().toString());
    var expectedConsortiumItems = List.of(
      toConsortiumItem(randomUUID().toString(), item(ids.get(0), MEMBER_TENANT_ID)),
      toConsortiumItem(randomUUID().toString(), item(ids.get(1), CENTRAL_TENANT_ID))
    );
    when(properties.getMaxSearchBatchRequestIdsCount()).thenReturn(10L);
    when(searchRepository.search(any(CqlSearchRequest.class), any(SearchSourceBuilder.class)))
      .thenReturn(mock(SearchResponse.class));
    when(documentConverter.convertToSearchResult(any(SearchResponse.class), eq(Instance.class), any()))
      .thenReturn(SearchResult.of(2, List.of(expectedConsortiumItems)));
    var expected = new ConsortiumItemCollection()
      .items(expectedConsortiumItems)
      .totalRecords(2);

    var result = service.fetchConsortiumBatchItems(CENTRAL_TENANT_ID, Sets.newHashSet(ids), IdentifierTypeEnum.ID);

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void fetchConsortiumBatchItems_positive_exceedsSearchResultWindow() {
    var ids = IntStream.range(1, 10002).mapToObj(i -> randomUUID().toString()).toList();
    var expectedConsortiumItems = List.of(
      toConsortiumItem(randomUUID().toString(), item(ids.get(0), MEMBER_TENANT_ID)),
      toConsortiumItem(randomUUID().toString(), item(ids.get(1), CENTRAL_TENANT_ID))
    );
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
    when(documentConverter.convertToSearchResult(eq(responseMock1), eq(Instance.class), any()))
      .thenReturn(SearchResult.of(2, List.of(expectedConsortiumItems)));
    var expected = new ConsortiumItemCollection()
      .items(expectedConsortiumItems)
      .totalRecords(2);

    var result = service.fetchConsortiumBatchItems(CENTRAL_TENANT_ID, Sets.newHashSet(ids), IdentifierTypeEnum.ID);

    assertThat(result).isEqualTo(expected);
    verify(searchRepository, times(2))
      .search(any(CqlSearchRequest.class), any(SearchSourceBuilder.class));
    verify(documentConverter)
      .convertToSearchResult(any(SearchResponse.class), eq(Instance.class), any());
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
      .holdingsRecordId(randomUUID().toString());
  }
}
