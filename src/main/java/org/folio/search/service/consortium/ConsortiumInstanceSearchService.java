package org.folio.search.service.consortium;

import static org.apache.commons.collections4.CollectionUtils.containsAny;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.search.converter.ConsortiumHoldingMapper.toConsortiumHolding;
import static org.folio.search.converter.ConsortiumItemMapper.toConsortiumItem;
import static org.folio.search.service.SearchService.DEFAULT_MAX_SEARCH_RESULT_WINDOW;
import static org.folio.search.utils.IdentifierUtils.getHoldingIdentifierValue;
import static org.folio.search.utils.IdentifierUtils.getHoldingTargetField;
import static org.folio.search.utils.IdentifierUtils.getItemIdentifierValue;
import static org.folio.search.utils.IdentifierUtils.getItemTargetField;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.termsQuery;
import static org.opensearch.search.sort.SortBuilders.fieldSort;
import static org.opensearch.search.sort.SortOrder.ASC;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.BatchIdsDto.IdentifierTypeEnum;
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

    return toConsortiumHolding(instance.getId(), holding);
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

    return toConsortiumItem(instance.getId(), item);
  }

  public ConsortiumHoldingCollection fetchConsortiumBatchHoldings(String tenant,
                                                                  Set<String> identifierValues,
                                                                  IdentifierTypeEnum identifierType) {
    validateIdsCount(identifierValues.size());

    var searchRecords = getConsortiumBatchResults(tenant,
      identifierType, identifierValues, getHoldingTargetField(identifierType), this::mapToConsortiumHolding);

    if (searchRecords.isEmpty()) {
      return new ConsortiumHoldingCollection();
    }

    return new ConsortiumHoldingCollection()
      .holdings(searchRecords.stream().map(SearchResult::getRecords).flatMap(List::stream).toList())
      .totalRecords(searchRecords.stream().map(SearchResult::getTotalRecords).mapToInt(i -> i).sum());
  }

  public ConsortiumItemCollection fetchConsortiumBatchItems(String tenant,
                                                            Set<String> identifierValues,
                                                            IdentifierTypeEnum identifierType) {
    validateIdsCount(identifierValues.size());

    var searchRecords = getConsortiumBatchResults(tenant,
      identifierType, identifierValues, getItemTargetField(identifierType), this::mapToConsortiumItem);

    if (searchRecords.isEmpty()) {
      return new ConsortiumItemCollection();
    }

    return new ConsortiumItemCollection()
      .items(searchRecords.stream().map(SearchResult::getRecords).flatMap(List::stream).toList())
      .totalRecords(searchRecords.stream().map(SearchResult::getTotalRecords).mapToInt(i -> i).sum());
  }

  private <T> List<SearchResult<T>> getConsortiumBatchResults(String tenant,
                                                              IdentifierTypeEnum identifierType,
                                                              Set<String> identifierValues,
                                                              String targetField,
                                                              Mapper<Instance, IdentifierTypeEnum, Set<String>, List<T>>
                                                                recordMapper) {
    var request = CqlSearchRequest.of(Instance.class, tenant, "", 0, 0, true, false, true);
    var termsQuery = termsQuery(targetField, identifierValues);

    if (identifierValues.size() < DEFAULT_MAX_SEARCH_RESULT_WINDOW) {
      var searchSourceBuilder = queryBuilder(termsQuery, identifierValues.size());
      var response = searchRepository.search(request, searchSourceBuilder);
      var searchResult = documentConverter.convertToSearchResult(response, request.getResourceClass(),
        (hits, item) -> recordMapper.apply(item, identifierType, identifierValues));
      var records = searchResult.getRecords().stream()
        .flatMap(List::stream)
        .toList();
      return List.of(SearchResult.of(records.size(), records));
    }

    var searchSourceBuilder = queryBuilder(termsQuery, properties.getSearchConsortiumRecordsPageSize())
      .sort(fieldSort(targetField).order(ASC))
      .searchAfter(new Object[]{""});
    var response = searchRepository.search(request, searchSourceBuilder);
    List<SearchResult<T>> searchRecords = new ArrayList<>();

    while (response.getHits() != null && response.getHits().getHits().length > 0) {
      var searchResult = documentConverter.convertToSearchResult(response, request.getResourceClass(),
        (hits, item) -> recordMapper.apply(item, identifierType, identifierValues));
      var records = searchResult.getRecords().stream()
        .flatMap(List::stream)
        .toList();
      searchRecords.add(SearchResult.of(records.size(), records));
      var searchAfterValue = response.getHits()
        .getAt(response.getHits().getHits().length - 1).getSortValues()[0];
      searchSourceBuilder.searchAfter(new Object[]{searchAfterValue});
      response = searchRepository.search(request, searchSourceBuilder);
    }

    return searchRecords;
  }

  private List<ConsortiumHolding> mapToConsortiumHolding(Instance instance,
                                                         IdentifierTypeEnum identifierType,
                                                         Set<String> identifierValues) {
    if (identifierType == IdentifierTypeEnum.ITEM_BARCODE) {
      return instance.getItems().stream()
        .filter(item -> identifierValues.contains(item.getBarcode()))
        .flatMap(item -> instance.getHoldings().stream()
          .filter(holding -> item.getHoldingsRecordId().equals(holding.getId()))
        )
        .map(holding -> toConsortiumHolding(instance.getId(), holding))
        .distinct()
        .toList();
    } else if (identifierType == IdentifierTypeEnum.INSTANCE_HRID) {
      return instance.getHoldings()
        .stream()
        .map(holding -> toConsortiumHolding(instance.getId(), holding))
        .toList();
    } else {
      return instance.getHoldings().stream()
        .filter(holding -> containsAny(identifierValues, getHoldingIdentifierValue(identifierType, holding)))
        .map(holding -> toConsortiumHolding(instance.getId(), holding))
        .toList();
    }
  }

  private List<ConsortiumItem> mapToConsortiumItem(Instance instance,
                                                   IdentifierTypeEnum identifierType,
                                                   Set<String> identifierValues) {
    return instance.getItems().stream()
      .filter(item -> containsAny(identifierValues, getItemIdentifierValue(identifierType, item)))
      .map(holding -> toConsortiumItem(instance.getId(), holding))
      .toList();
  }

  private SearchSourceBuilder queryBuilder(QueryBuilder filterQuery, int size) {
    return new SearchSourceBuilder()
      .query(boolQuery().filter(filterQuery))
      .size(size)
      .from(0)
      .trackTotalHits(true);
  }

  private void validateIdsCount(long count) {
    var idsLimit = properties.getMaxSearchBatchRequestIdsCount();
    if (count > idsLimit) {
      throw new RequestValidationException("IDs array size exceeds the maximum allowed limit %s".formatted(idsLimit),
        "size", Long.toString(count));
    }
  }

  @FunctionalInterface
  public interface Mapper<T, U, V, R> {
    R apply(T t, U u, V v);
  }
}
