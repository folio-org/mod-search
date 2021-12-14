package org.folio.search.service;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.script.Script.DEFAULT_SCRIPT_LANG;
import static org.elasticsearch.script.ScriptType.INLINE;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.elasticsearch.search.sort.ScriptSortBuilder.ScriptSortType.NUMBER;
import static org.elasticsearch.search.sort.SortBuilders.scriptSort;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToList;
import static org.folio.search.utils.CollectionUtils.reverse;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.configuration.properties.SearchQueryConfigurationProperties;
import org.folio.search.cql.CqlQueryParser;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.model.Pair;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.CallNumberBrowseRequest;
import org.folio.search.model.service.CallNumberServiceContext;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.search.service.setter.instance.CallNumberProcessor;
import org.springframework.stereotype.Service;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLTermNode;

@Service
@RequiredArgsConstructor
public class CallNumberBrowseService {

  public static final String CALL_NUMBER_BROWSING_FIELD = "callNumber";

  static final String SORT_SCRIPT_FOR_SUCCEEDING_QUERY = getSortingScript(true);
  static final String SORT_SCRIPT_FOR_PRECEDING_QUERY = getSortingScript(false);

  private final CqlQueryParser cqlQueryParser;
  private final SearchRepository searchRepository;
  private final CallNumberProcessor callNumberProcessor;
  private final CqlSearchQueryConverter cqlSearchQueryConverter;
  private final ElasticsearchDocumentConverter documentConverter;
  private final SearchQueryConfigurationProperties queryConfiguration;

  /**
   * Finds related instances for call number browsing using given {@link CallNumberBrowseRequest} object.
   *
   * @param request - service request as {@link CallNumberBrowseRequest} object
   * @return search result with related instances by virtual shelf.
   */
  public SearchResult<CallNumberBrowseItem> browseByCallNumber(CallNumberBrowseRequest request) {
    var cqlSearchSource = cqlSearchQueryConverter.convert(request.getQuery(), request.getResource());
    var offset = (long) queryConfiguration.getCallNumberRangeOffset();
    var context = CallNumberServiceContext.of(request, cqlSearchSource).withUpdatedRanges(offset);
    if (context.isBrowsingAround()) {
      return browseByCallNumberAround(request, context);
    }

    var searchSource = getSearchSource(context, context.isForwardBrowsing());
    var searchResponse = searchRepository.search(request, searchSource);
    return convertToSearchResult(searchResponse, context, context.isForwardBrowsing());
  }


  private SearchResult<CallNumberBrowseItem> browseByCallNumberAround(
    CallNumberBrowseRequest request, CallNumberServiceContext context) {
    var multiSearchResponse = searchRepository.msearch(request,
      List.of(getSearchSource(context, false), getSearchSource(context, true)));

    var responses = multiSearchResponse.getResponses();
    var precedingResult = convertToSearchResult(responses[0].getResponse(), context, false);
    var succeedingResult = convertToSearchResult(responses[1].getResponse(), context, true);
    highlightMatchingCallNumber(request, succeedingResult);

    return SearchResult.of(
      precedingResult.getTotalRecords() + succeedingResult.getTotalRecords(),
      mergeSafelyToList(precedingResult.getRecords(), succeedingResult.getRecords()));
  }

  private SearchSourceBuilder getSearchSource(CallNumberServiceContext ctx, boolean isForwardBrowsing) {
    var scriptCode = isForwardBrowsing ? SORT_SCRIPT_FOR_SUCCEEDING_QUERY : SORT_SCRIPT_FOR_PRECEDING_QUERY;
    var script = new Script(INLINE, DEFAULT_SCRIPT_LANG, scriptCode, Map.of("anchor", ctx.getAnchor()));

    var query = isForwardBrowsing ? ctx.getSucceedingQuery() : ctx.getPrecedingQuery();
    return searchSource().from(0).trackTotalHits(true)
      .query(getQuery(ctx.getFilters(), query))
      .size((int) (Math.ceil(ctx.getLimit(isForwardBrowsing) * queryConfiguration.getRangeQueryLimitMultiplier())))
      .sort(scriptSort(script, NUMBER));
  }

  private static QueryBuilder getQuery(List<QueryBuilder> filters, RangeQueryBuilder rangeQuery) {
    if (filters.isEmpty()) {
      return rangeQuery;
    }

    var boolQuery = boolQuery().must(rangeQuery);
    filters.forEach(boolQuery::filter);
    return boolQuery;
  }

  private SearchResult<CallNumberBrowseItem> convertToSearchResult(
    SearchResponse response, CallNumberServiceContext ctx, boolean isForwardBrowsing) {
    var searchResult = documentConverter.convertToSearchResult(response, Instance.class)
      .map(instance -> mapToCallNumberBrowseItem(ctx, isForwardBrowsing, instance));

    var collapsedItems = collapseSearchResultByCallNumber(searchResult.getRecords());
    return searchResult.records(trim(ctx, isForwardBrowsing, collapsedItems));
  }

  private static List<CallNumberBrowseItem> trim(
    CallNumberServiceContext ctx, boolean isForwardBrowsing, List<CallNumberBrowseItem> items) {
    return isForwardBrowsing
      ? items.subList(0, min(ctx.getLimit(true), items.size()))
      : reverse(items).subList(max(items.size() - ctx.getLimit(false), 0), items.size());
  }

  private CallNumberBrowseItem mapToCallNumberBrowseItem(
    CallNumberServiceContext ctx, boolean isForwardBrowsing, Instance instance) {
    return new CallNumberBrowseItem()
      .callNumber(getCallNumber(instance, ctx, isForwardBrowsing))
      .instance(instance)
      .totalRecords(1);
  }

  private String getCallNumber(Instance instance, CallNumberServiceContext ctx, boolean isForwardBrowsing) {
    var callNumbers = toStreamSafe(instance.getItems())
      .map(Item::getEffectiveShelvingOrder)
      .filter(Objects::nonNull)
      .collect(toList());

    if (callNumbers.size() == 1) {
      return callNumbers.get(0);
    }

    var isAnchorIncluded = isForwardBrowsing
      ? ctx.getSucceedingQuery().includeLower()
      : ctx.getPrecedingQuery().includeUpper();

    return callNumbers.stream()
      .map(cn -> Pair.of(cn, getDifferenceBetweenCallNumberAndAnchor(ctx.getAnchor(), cn, isForwardBrowsing)))
      .filter(pair -> isAnchorIncluded ? pair.getSecond() >= 0 : pair.getSecond() > 0)
      .min(Comparator.comparing(Pair::getSecond))
      .map(Pair::getFirst)
      .orElse(null);
  }

  private void highlightMatchingCallNumber(CallNumberBrowseRequest request, SearchResult<CallNumberBrowseItem> result) {
    var items = result.getRecords();
    var cqlNode = cqlQueryParser.parseCqlQuery(request.getQuery(), request.getResource());
    var anchorCallNumber = getAnchorCallNumber(cqlNode);
    if (isEmpty(items)) {
      result.setRecords(Collections.singletonList(getEmptyCallNumberBrowseItem(anchorCallNumber)));
      return;
    }

    var firstItem = items.get(0);
    var callNumber = firstItem.getCallNumber();
    if (!Objects.equals(callNumber, anchorCallNumber)) {
      items.add(0, getEmptyCallNumberBrowseItem(anchorCallNumber));
      items.remove(items.size() - 1);
      return;
    }

    firstItem.setCallNumber("<mark>" + callNumber + "</mark>");
  }

  private long getDifferenceBetweenCallNumberAndAnchor(Long anchor, String value, boolean isForwardBrowsing) {
    var longValue = callNumberProcessor.getCallNumberAsLong(value);
    return isForwardBrowsing ? longValue - anchor : anchor - longValue;
  }

  private static CallNumberBrowseItem getEmptyCallNumberBrowseItem(String anchorCallNumber) {
    return new CallNumberBrowseItem().callNumber(anchorCallNumber).totalRecords(0);
  }

  private static String getAnchorCallNumber(CQLNode node) {
    if (node instanceof CQLTermNode) {
      var termNode = (CQLTermNode) node;
      if (CALL_NUMBER_BROWSING_FIELD.equals(termNode.getIndex())) {
        return termNode.getTerm();
      }
    }

    if (node instanceof CQLBooleanNode) {
      var boolNode = (CQLBooleanNode) node;
      var rightAnchorCallNumber = getAnchorCallNumber(boolNode.getRightOperand());
      return rightAnchorCallNumber != null ? rightAnchorCallNumber : getAnchorCallNumber(boolNode.getLeftOperand());
    }

    return null;
  }

  private static List<CallNumberBrowseItem> collapseSearchResultByCallNumber(List<CallNumberBrowseItem> items) {
    if (items.isEmpty()) {
      return emptyList();
    }

    var collapsedItems = new ArrayList<CallNumberBrowseItem>();
    var iterator = items.iterator();
    var prevItem = iterator.next();
    collapsedItems.add(prevItem);

    CallNumberBrowseItem currItem;
    while (iterator.hasNext()) {
      currItem = iterator.next();
      if (Objects.equals(prevItem.getCallNumber(), currItem.getCallNumber())) {
        prevItem.instance(null).totalRecords(prevItem.getTotalRecords() + 1);
        continue;
      }
      collapsedItems.add(currItem);
      prevItem = currItem;
    }

    return collapsedItems;
  }

  private static String getSortingScript(boolean isBrowsingForward) {
    return "if (doc['callNumber'].size() == 0) return Long.MAX_VALUE; long m = "
      + (isBrowsingForward ? "doc['callNumber'].value - params.anchor" : "params.anchor - doc['callNumber'].value")
      + "; for (int i = 1; i < doc['callNumber'].length; i++) { long c = "
      + (isBrowsingForward ? "doc['callNumber'][i] - params.anchor" : "params.anchor - doc['callNumber'][i]")
      + "; if (c >= 0 && c < m) m = c;" + "} return m >= 0 ? m : Long.MAX_VALUE;";
  }
}
