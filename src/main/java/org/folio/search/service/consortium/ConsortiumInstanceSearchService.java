package org.folio.search.service.consortium;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.search.utils.SearchUtils.INSTANCE_HOLDING_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.INSTANCE_ITEM_FIELD_NAME;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.termsQuery;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortOrder.ASC;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
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
  //private static final int DEFAULT_SEARCH_MAX_CLAUSE_COUNT = 10;

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
    var targetField = INSTANCE_HOLDING_FIELD_NAME + ".id";

    //if (ids.size() < DEFAULT_SEARCH_MAX_CLAUSE_COUNT) {
    //  var searchRequest = idsCqlRequest(tenant, targetField, ids);
    //  var result = searchService.search(searchRequest);
    //  var consortiumHoldings = result.getRecords().stream()
    //    .map(instance -> mapToConsortiumHolding(instance, ids))
    //    .toList();
    //  return new ConsortiumHoldingCollection()
    //    .holdings(consortiumHoldings)
    //    .totalRecords(consortiumHoldings.size());
    //}

    var searchRecords = getConsortiumBatchResults(tenant, ids, targetField, this::mapToConsortiumHolding);

    if (searchRecords.isEmpty()) {
      return new ConsortiumHoldingCollection();
    }

    return new ConsortiumHoldingCollection()
      .holdings(searchRecords.stream().map(SearchResult::getRecords).flatMap(List::stream).toList())
      .totalRecords(searchRecords.iterator().next().getTotalRecords());
  }

  public ConsortiumItemCollection fetchConsortiumBatchItems(String tenant, Set<UUID> itemIds) {
    validateIdsCount(itemIds.size());
    //var result = searchService.search(searchRequest);
    //var consortiumItems = result.getRecords().stream()
    //  .flatMap(instance ->
    //    instance.getItems().stream()
    //      .filter(item -> ids.contains(item.getId()))
    //      .map(item -> ConsortiumItemMapper.toConsortiumItem(instance.getId(), item))
    //  )
    //  .toList();

    var ids = itemIds.stream().map(UUID::toString).collect(Collectors.toSet());
    var targetField = INSTANCE_ITEM_FIELD_NAME + ".id";

    var searchRecords = getConsortiumBatchResults(tenant, ids, targetField, this::mapToConsortiumItem);

    if (searchRecords.isEmpty()) {
      return new ConsortiumItemCollection();
    }

    return new ConsortiumItemCollection()
      .items(searchRecords.stream().map(SearchResult::getRecords).flatMap(List::stream).toList())
      .totalRecords(searchRecords.iterator().next().getTotalRecords());
  }

  private <T> List<SearchResult<T>> getConsortiumBatchResults(String tenant, Set<String> ids, String targetField,
                                                              BiFunction<Instance, Set<String>, T> recordMapper) {
    //if (ids.size() < DEFAULT_SEARCH_MAX_CLAUSE_COUNT) {
    //  var searchRequest = idsCqlRequest(tenant, targetField, ids);
    //  var searchResult = searchService.search(searchRequest);
    //  var consortiumHoldings = searchResult.getRecords().stream()
    //    .map(instance -> mapToConsortiumHolding(instance, ids))
    //    .toList();
    //  return new ConsortiumHoldingCollection()
    //    .holdings(consortiumHoldings)
    //    .totalRecords(searchResult.getTotalRecords());
    //}

    var request = CqlSearchRequest.of(Instance.class, tenant, "", 0, 0, true, false, true);
    var termsQuery = termsQuery(targetField, ids);

    //DEFAULT_MAX_SEARCH_RESULT_WINDOW
    if (ids.size() < 10) {
      var searchSourceBuilder = getConsortiumBatchQueryBuilder(targetField, termsQuery, ids.size());
      var response = searchRepository.search(request, searchSourceBuilder);
      var searchResult = documentConverter.convertToSearchResult(response, request.getResourceClass(),
        (hits, item) -> recordMapper.apply(item, ids));
      return List.of(searchResult);
    }

    var searchSourceBuilder = getConsortiumBatchQueryBuilder(targetField, termsQuery,
      properties.getSearchConsortiumRecordsPageSize());
    var response = searchRepository.search(request, searchSourceBuilder);
    List<SearchResult<T>> searchRecords = new ArrayList<>();

    while (response.getHits() != null && response.getHits().getHits().length > 0) {
      var searchResult = documentConverter.convertToSearchResult(response, request.getResourceClass(),
        (hits, item) -> recordMapper.apply(item, ids));
      searchRecords.add(searchResult);
      var searchAfterValue = response.getHits()
        .getAt(response.getHits().getHits().length - 1).getSortValues()[0];
      searchSourceBuilder.searchAfter(new Object[]{searchAfterValue});
      response = searchRepository.search(request, searchSourceBuilder);
    }

    return searchRecords;
  }

  private ConsortiumHolding mapToConsortiumHolding(Instance instance, Set<String> ids) {
    var consortiumHolding = instance.getHoldings().stream()
      .filter(holding -> ids.contains(holding.getId()))
      .map(holding -> ConsortiumHoldingMapper.toConsortiumHolding(instance.getId(), holding))
      .findFirst()
      .orElse(null);

    if (consortiumHolding == null) {
      log.warn("No holding was found in instance with id: {}, returned for the query to fetch holdings"
        + " by batch holdings IDs", instance.getId());
    }

    return consortiumHolding;
  }

  private ConsortiumItem mapToConsortiumItem(Instance instance, Set<String> ids) {
    var consortiumItem = instance.getItems().stream()
      .filter(item -> ids.contains(item.getId()))
      .map(holding -> ConsortiumItemMapper.toConsortiumItem(instance.getId(), holding))
      .findFirst()
      .orElse(null);

    if (consortiumItem == null) {
      log.warn("No Item was found in instance with id: {}, returned for the query to fetch items"
        + " by batch items IDs", instance.getId());
    }

    return consortiumItem;
  }

  private SearchSourceBuilder getConsortiumBatchQueryBuilder(String targetField,
                                                             QueryBuilder filterQuery,
                                                             int size) {
    var builder = new SearchSourceBuilder()
      .query(boolQuery().filter(filterQuery))
      .size(size)
      .from(0)
      .trackTotalHits(true);

    if (targetField != null) {
      builder.sort(fieldSort(targetField).order(ASC));
    }

    return builder;
  }

  //private CqlSearchRequest<Instance> idsCqlRequest(String tenant, String fieldName, Set<String> ids) {
  //  var query = ids.stream()
  //    .map((fieldName + "=%s")::formatted)
  //    .collect(Collectors.joining(" or "));
  //  return CqlSearchRequest.of(Instance.class, tenant, query, ids.size(), 0, true, false, true);
  //}

  private void validateIdsCount(long count) {
    var idsLimit = properties.getMaxSearchBatchRequestIdsCount();
    if (count > idsLimit) {
      throw new RequestValidationException("IDs array size exceeds the maximum allowed limit %s".formatted(idsLimit),
        "size", Long.toString(count));
    }
  }
}
