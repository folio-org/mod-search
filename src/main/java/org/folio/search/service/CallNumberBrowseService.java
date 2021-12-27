package org.folio.search.service;

import static java.lang.Boolean.TRUE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.script.Script.DEFAULT_SCRIPT_LANG;
import static org.elasticsearch.script.ScriptType.INLINE;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.elasticsearch.search.sort.ScriptSortBuilder.ScriptSortType.NUMBER;
import static org.elasticsearch.search.sort.SortBuilders.scriptSort;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToList;
import static org.folio.search.utils.CollectionUtils.reverse;
import static org.folio.search.utils.SearchUtils.getEffectiveCallNumber;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
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
import org.folio.search.service.metadata.SearchFieldProvider;
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
  private final SearchFieldProvider searchFieldProvider;
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

    var searchSource = getSearchSource(request, context, context.isForwardBrowsing());
    var searchResponse = searchRepository.search(request, searchSource);
    return convertToSearchResult(searchResponse, context, context.isForwardBrowsing());
  }

  private SearchResult<CallNumberBrowseItem> browseByCallNumberAround(
    CallNumberBrowseRequest request, CallNumberServiceContext context) {
    var multiSearchResponse = searchRepository.msearch(request,
      List.of(getSearchSource(request, context, false), getSearchSource(request, context, true)));

    var responses = multiSearchResponse.getResponses();
    var precedingResult = convertToSearchResult(responses[0].getResponse(), context, false);
    var succeedingResult = convertToSearchResult(responses[1].getResponse(), context, true);

    if (TRUE.equals(request.getHighlightMatch())) {
      highlightMatchingCallNumber(request, context, succeedingResult);
    }

    return SearchResult.of(
      precedingResult.getTotalRecords() + succeedingResult.getTotalRecords(),
      mergeSafelyToList(precedingResult.getRecords(), succeedingResult.getRecords()));
  }

  private SearchSourceBuilder getSearchSource(CallNumberBrowseRequest request, CallNumberServiceContext ctx,
    boolean isForwardBrowsing) {
    var scriptCode = isForwardBrowsing ? SORT_SCRIPT_FOR_SUCCEEDING_QUERY : SORT_SCRIPT_FOR_PRECEDING_QUERY;
    var script = new Script(INLINE, DEFAULT_SCRIPT_LANG, scriptCode, Map.of("anchor", ctx.getAnchor()));
    var query = isForwardBrowsing ? ctx.getSucceedingQuery() : ctx.getPrecedingQuery();

    var searchSource = searchSource().from(0).trackTotalHits(true)
      .query(getQuery(ctx.getFilters(), query))
      .size((int) (Math.ceil(ctx.getLimit(isForwardBrowsing) * queryConfiguration.getRangeQueryLimitMultiplier())))
      .sort(scriptSort(script, NUMBER));

    if (isFalse(request.getExpandAll())) {
      var includes = searchFieldProvider.getSourceFields(request.getResource()).toArray(String[]::new);
      searchSource.fetchSource(includes, null);
    }

    return searchSource;
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
    var item = getShelfKey(instance, ctx, isForwardBrowsing);
    var callNumberBrowseItem = new CallNumberBrowseItem().instance(instance).totalRecords(1);

    if (item != null) {
      callNumberBrowseItem.shelfKey(item.getEffectiveShelvingOrder());
      var cn = item.getEffectiveCallNumberComponents();
      if (cn != null) {
        callNumberBrowseItem.fullCallNumber(getEffectiveCallNumber(cn.getPrefix(), cn.getCallNumber(), cn.getSuffix()));
      }
    }

    return callNumberBrowseItem;
  }

  private Item getShelfKey(Instance instance, CallNumberServiceContext ctx, boolean isForwardBrowsing) {
    var items = instance.getItems();
    if (CollectionUtils.isEmpty(items)) {
      return null;
    }

    if (items.size() == 1) {
      return items.get(0);
    }

    var isAnchorIncluded = isForwardBrowsing
      ? ctx.getSucceedingQuery().includeLower()
      : ctx.getPrecedingQuery().includeUpper();

    return items.stream()
      .filter(item -> item.getEffectiveShelvingOrder() != null)
      .map(item -> Pair.of(item, getDifferenceBetweenCallNumberAndAnchor(ctx.getAnchor(), item, isForwardBrowsing)))
      .filter(pair -> isAnchorIncluded ? pair.getSecond() >= 0 : pair.getSecond() > 0)
      .min(Comparator.comparing(Pair::getSecond))
      .map(Pair::getFirst)
      .orElse(null);
  }

  private void highlightMatchingCallNumber(CallNumberBrowseRequest request, CallNumberServiceContext ctx,
    SearchResult<CallNumberBrowseItem> result) {
    var items = result.getRecords();
    var cqlNode = cqlQueryParser.parseCqlQuery(request.getQuery(), request.getResource());
    var anchorCallNumber = getAnchorCallNumber(cqlNode);

    if (isEmpty(items)) {
      result.setRecords(singletonList(getEmptyCallNumberBrowseItem(anchorCallNumber)));
      return;
    }

    var firstItem = items.get(0);
    var anchorAsLong = ctx.getAnchor();
    var shelfKeyAsLong = callNumberProcessor.getCallNumberAsLong(firstItem.getShelfKey());
    if (anchorAsLong != shelfKeyAsLong) {
      items.add(0, getEmptyCallNumberBrowseItem(anchorCallNumber));
      items.remove(items.size() - 1);
      return;
    }

    firstItem.setFullCallNumber("<mark>" + firstItem.getFullCallNumber() + "</mark>");
  }

  private long getDifferenceBetweenCallNumberAndAnchor(Long anchor, Item item, boolean isForwardBrowsing) {
    var longValue = callNumberProcessor.getCallNumberAsLong(item.getEffectiveShelvingOrder());
    return isForwardBrowsing ? longValue - anchor : anchor - longValue;
  }

  private static CallNumberBrowseItem getEmptyCallNumberBrowseItem(String anchorCallNumber) {
    return new CallNumberBrowseItem().shelfKey(anchorCallNumber).totalRecords(0);
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
      if (Objects.equals(prevItem.getShelfKey(), currItem.getShelfKey())) {
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
