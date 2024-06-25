package org.folio.search.service.consortium;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.search.utils.SearchUtils.INSTANCE_HOLDING_FIELD_NAME;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.termsQuery;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortOrder.ASC;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.converter.ConsortiumHoldingMapper;
import org.folio.search.converter.ConsortiumItemMapper;
import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.ConsortiumHoldingCollection;
import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.search.domain.dto.ConsortiumItemCollection;
import org.folio.search.domain.dto.Instance;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.SearchService;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.spring.FolioExecutionContext;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

/**
 * Class designed to be executed only in scope of consortium central tenant id.
 * So, it can be expected to always have central tenant id in {@link FolioExecutionContext}.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ConsortiumInstanceSearchService {

  /**
   * default maximum number of allowed clauses in a (Lucene) boolean query which is defined by opensearch's
   * "indices.query.bool.max_clause_count" setting
   */
  private static final int DEFAULT_SEARCH_MAX_CLAUSE_COUNT = 10;

  private final SearchService searchService;
  private final SearchRepository searchRepository;
  private final ElasticsearchDocumentConverter documentConverter;
  private final SearchConfigurationProperties properties;

  public ConsortiumHolding getConsortiumHolding(String id, CqlSearchRequest<Instance> searchRequest) {
    var result = searchService.search(searchRequest);

    if (isEmpty(result.getRecords()) || isEmpty(result.getRecords().iterator().next().getHoldings())) {
      return new ConsortiumHolding();
    }

    var instance = result.getRecords().iterator().next();
    var holding = instance.getHoldings().stream()
      .filter(hol -> Objects.equals(id, hol.getId()))
      .findFirst().orElse(null);

    if (holding == null) {
      return new ConsortiumHolding();
    }

    return ConsortiumHoldingMapper.toConsortiumHolding(instance.getId(), holding);
  }

  public ConsortiumItem getConsortiumItem(String id, CqlSearchRequest<Instance> searchRequest) {
    var result = searchService.search(searchRequest);

    if (isEmpty(result.getRecords()) || isEmpty(result.getRecords().iterator().next().getItems())) {
      return new ConsortiumItem();
    }

    var instance = result.getRecords().iterator().next();
    var item = instance.getItems().stream()
      .filter(it -> Objects.equals(id, it.getId()))
      .findFirst().orElse(null);

    if (item == null) {
      return new ConsortiumItem();
    }

    return ConsortiumItemMapper.toConsortiumItem(instance.getId(), item);
  }

  public ConsortiumHoldingCollection fetchConsortiumBatchHoldings(String tenant, Set<UUID> holdingIds) {
    validateIdsCount(holdingIds.size());

    var ids = holdingIds.stream().map(UUID::toString).collect(Collectors.toSet());
    var results = getConsortiumBatchResult(tenant, ids, INSTANCE_HOLDING_FIELD_NAME + ".id");
    var consortiumHoldings = results.stream().map(SearchResult::getRecords).flatMap(List::stream)
      .flatMap(instance ->
        instance.getHoldings().stream()
          .filter(holding -> ids.contains(holding.getId()))
          .map(holding -> ConsortiumHoldingMapper.toConsortiumHolding(instance.getId(), holding))
      )
      .toList();
    return new ConsortiumHoldingCollection()
      .holdings(consortiumHoldings)
      .totalRecords(consortiumHoldings.size());
  }

  private List<SearchResult<Instance>> getConsortiumBatchResult(String tenant, Set<String> ids, String targetField) {
    if (ids.size() < DEFAULT_SEARCH_MAX_CLAUSE_COUNT) {
      var searchRequest = idsCqlRequest(tenant, INSTANCE_HOLDING_FIELD_NAME, ids);
      return List.of(searchService.search(searchRequest));
    }

    var request = CqlSearchRequest.of(Instance.class, tenant, "", 0, 0, true, false, true);
    var termsQuery = termsQuery(targetField, ids);

//    if (ids.size() < 10_000L) {
//      var searchSourceBuilder = getConsortiumBatchQueryBuilder(targetField, termsQuery, "", ids.size());
//      var response = searchRepository.search(request, searchSourceBuilder);
//      return List.of(documentConverter.convertToSearchResult(response, request.getResourceClass()));
//    }

    var searchSourceBuilder = getConsortiumBatchQueryBuilder(targetField, termsQuery, "", 2);
    var response = searchRepository.search(request, searchSourceBuilder);
    List<SearchResult<Instance>> searchResults = new ArrayList<>();

    while (response.getHits() != null && response.getHits().getHits().length > 0) {
      log.info("TOTAL HITS: {}", response.getHits().getTotalHits());
      var searchResult = documentConverter.convertToSearchResult(response, request.getResourceClass());
      searchResults.add(searchResult);
      var searchAfterValue = response.getHits()
        .getAt(response.getHits().getHits().length - 1).getSortValues()[0];
      searchSourceBuilder.searchAfter(new Object[]{searchAfterValue});
      response = searchRepository.search(request, searchSourceBuilder);
    }

    return searchResults;
  }

  public ConsortiumItemCollection fetchConsortiumBatchItems(CqlSearchRequest<Instance> searchRequest,
                                                            Set<String> ids) {
    validateIdsCount(ids.size());
    var result = searchService.search(searchRequest);
    var consortiumItems = result.getRecords().stream()
      .flatMap(instance ->
        instance.getItems().stream()
          .filter(item -> ids.contains(item.getId()))
          .map(item -> ConsortiumItemMapper.toConsortiumItem(instance.getId(), item))
      )
      .toList();

    return new ConsortiumItemCollection()
      .items(consortiumItems)
      .totalRecords(consortiumItems.size());
  }

  private SearchSourceBuilder getConsortiumBatchQueryBuilder(String targetField,
                                                             QueryBuilder filterQuery,
                                                             Object searchAfterValue,
                                                             int size) {
    var builder = new SearchSourceBuilder()
      .query(boolQuery().filter(filterQuery))
      .size(size)
      .from(0)
      .trackTotalHits(true);

    if (targetField != null && searchAfterValue != null) {
      builder.sort(fieldSort(targetField).order(ASC))
        .searchAfter(new Object[]{searchAfterValue});
    }

    return builder;
  }

  private CqlSearchRequest<Instance> idsCqlRequest(String tenant, String fieldName, Set<String> ids) {
    var query = ids.stream()
      .map((fieldName + ".id=%s")::formatted)
      .collect(Collectors.joining(" or "));

    return CqlSearchRequest.of(Instance.class, tenant, query, ids.size(), 0, true, false, true);
  }

  private void validateIdsCount(long count) {
    var idsLimit = properties.getMaxSearchBatchRequestIdsCount();
    if (count > idsLimit) {
      throw new RequestValidationException("IDs array size exceeds the maximum allowed limit %s".formatted(idsLimit),
        "size", Long.toString(count));
    }
  }
}
