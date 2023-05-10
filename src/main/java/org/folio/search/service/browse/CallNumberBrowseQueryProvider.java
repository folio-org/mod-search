package org.folio.search.service.browse;

import static java.util.Collections.singletonMap;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.folio.search.model.types.ResponseGroupType.CN_BROWSE;
import static org.folio.search.utils.CallNumberUtils.getCallNumberAsLong;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;
import static org.opensearch.script.Script.DEFAULT_SCRIPT_LANG;
import static org.opensearch.script.ScriptType.INLINE;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.opensearch.search.sort.ScriptSortBuilder.ScriptSortType.STRING;
import static org.opensearch.search.sort.SortBuilders.scriptSort;
import static org.opensearch.search.sort.SortOrder.ASC;
import static org.opensearch.search.sort.SortOrder.DESC;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchQueryConfigurationProperties;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.model.types.CallNumberType;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.script.Script;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class CallNumberBrowseQueryProvider {

  private static final String SORT_SCRIPT_FOR_SUCCEEDING_QUERY = getSortingScript(true);
  private static final String SORT_SCRIPT_FOR_PRECEDING_QUERY = getSortingScript(false);
  private static final int MIN_QUERY_SIZE = 25;

  private final SearchFieldProvider searchFieldProvider;
  private final SearchQueryConfigurationProperties queryConfiguration;
  private final CallNumberBrowseRangeService callNumberBrowseRangeService;

  /**
   * Creates query as {@link SearchSourceBuilder} object for call number browsing.
   *
   * @param request           - {@link BrowseRequest} object
   * @param ctx               - {@link BrowseContext} object with parsed and validated queries, anchor, limits
   * @param isBrowsingForward - defines the direction of browsing
   * @return created Elasticsearch query as {@link SearchSourceBuilder} object
   */
  public SearchSourceBuilder get(BrowseRequest request, BrowseContext ctx, boolean isBrowsingForward) {
    log.debug("get:: by [tenant: {}, query: {}, isBrowsingForward: {}]",
      request.getTenantId(), request.getQuery(), isBrowsingForward);

    var scriptCode = isBrowsingForward ? SORT_SCRIPT_FOR_SUCCEEDING_QUERY : SORT_SCRIPT_FOR_PRECEDING_QUERY;
    var script = new Script(INLINE, DEFAULT_SCRIPT_LANG, scriptCode, singletonMap("cn", ctx.getAnchor()));

    var multiplier = queryConfiguration.getRangeQueryLimitMultiplier();
    var pageSize = (int) Math.max(MIN_QUERY_SIZE, Math.ceil(ctx.getLimit(isBrowsingForward) * multiplier));
    var searchSource = searchSource().from(0).size(pageSize)
      .query(getQuery(ctx, request, pageSize, isBrowsingForward))
      .sort(scriptSort(script, STRING).order(isBrowsingForward ? ASC : DESC));

    if (isFalse(request.getExpandAll())) {
      var includes = searchFieldProvider.getSourceFields(request.getResource(), CN_BROWSE);
      searchSource.fetchSource(includes, null);
    }

    return searchSource;
  }

  private QueryBuilder getQuery(BrowseContext ctx, BrowseRequest request, int size, boolean isBrowsingForward) {
    log.debug("getQuery:: by [tenant: {}, size: {}, isBrowsingForward: {}]", request.getTenantId(), size,
      isBrowsingForward);
    var anchor = ctx.getAnchor();
    var refinedCondition = request.getRefinedCondition();
    var callNumberType = CallNumberType.fromName(refinedCondition);
    var cnTypeNumber = callNumberType.map(CallNumberType::getNumber).orElse(-1);
    var callNumberAsLong = callNumberType
      .map(cnt -> getCallNumberAsLong(anchor, cnt.getNumber()))
      .orElse(getCallNumberAsLong(anchor));

    var rangeQuery = rangeQuery(request.getSubField());
    long defaultBoundary;
    if (isBrowsingForward) {
      defaultBoundary = getCallNumberAsLong("", cnTypeNumber + 1);
      rangeQuery = rangeQuery.gte(callNumberAsLong).lte(defaultBoundary);
    } else {
      defaultBoundary = getCallNumberAsLong("", cnTypeNumber);
      rangeQuery = rangeQuery.lte(callNumberAsLong).gte(defaultBoundary);
    }

    if (queryConfiguration.isCallNumberBrowseOptimizationEnabled()) {
      var boundary = callNumberBrowseRangeService
        .getRangeBoundaryForBrowsing(request.getTenantId(), anchor, request.getSubField(), cnTypeNumber, size,
          isBrowsingForward)
        .orElse(defaultBoundary == 0 ? null : defaultBoundary);
      rangeQuery = isBrowsingForward ? rangeQuery.lte(boundary) : rangeQuery.gte(boundary);
    }

    var filters = ctx.getFilters();
    if (filters.isEmpty()) {
      return rangeQuery;
    }

    var boolQuery = boolQuery().must(rangeQuery);
    filters.forEach(boolQuery::filter);
    return boolQuery;
  }

  private static String getSortingScript(boolean isBrowsingForward) {
    return "def f=doc['itemEffectiveShelvingOrder'];"
      + "def a=Collections.binarySearch(f,params['cn']);"
      + "if(a>=0) return f[a];a=-a-" + (isBrowsingForward ? "1" : "2")
      + ";f[(int)Math.min(Math.max(0, a),f.length-1)]";
  }
}
