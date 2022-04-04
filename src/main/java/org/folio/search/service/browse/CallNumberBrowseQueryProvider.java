package org.folio.search.service.browse;

import static java.util.Collections.singletonMap;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.script.Script.DEFAULT_SCRIPT_LANG;
import static org.elasticsearch.script.ScriptType.INLINE;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.elasticsearch.search.sort.ScriptSortBuilder.ScriptSortType.STRING;
import static org.elasticsearch.search.sort.SortBuilders.scriptSort;
import static org.elasticsearch.search.sort.SortOrder.ASC;
import static org.elasticsearch.search.sort.SortOrder.DESC;

import lombok.RequiredArgsConstructor;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.configuration.properties.SearchQueryConfigurationProperties;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallNumberBrowseQueryProvider {

  public static final String CALL_NUMBER_RANGE_FIELD = "callNumber";
  public static final String SORT_SCRIPT_FOR_SUCCEEDING_QUERY = getSortingScript(true);
  public static final String SORT_SCRIPT_FOR_PRECEDING_QUERY = getSortingScript(false);
  private static final int MIN_QUERY_SIZE = 25;

  private final SearchFieldProvider searchFieldProvider;
  private final CallNumberTermConverter callNumberTermConverter;
  private final SearchQueryConfigurationProperties queryConfiguration;
  private final CallNumberBrowseRangeService callNumberBrowseRangeService;

  /**
   * Creates query as {@link SearchSourceBuilder} object for call number browsing.
   *
   * @param request - {@link BrowseRequest} object
   * @param ctx - {@link BrowseContext} object with parsed and validated queries, anchor, limits
   * @param isBrowsingForward - defines the direction of browsing
   * @return created Elasticsearch query as {@link SearchSourceBuilder} object
   */
  public SearchSourceBuilder get(BrowseRequest request, BrowseContext ctx, boolean isBrowsingForward) {
    var scriptCode = isBrowsingForward ? SORT_SCRIPT_FOR_SUCCEEDING_QUERY : SORT_SCRIPT_FOR_PRECEDING_QUERY;
    var script = new Script(INLINE, DEFAULT_SCRIPT_LANG, scriptCode, singletonMap("cn", ctx.getAnchor()));

    var multiplier = queryConfiguration.getRangeQueryLimitMultiplier();
    var pageSize = (int) Math.max(MIN_QUERY_SIZE, Math.ceil(ctx.getLimit(isBrowsingForward) * multiplier));
    var searchSource = searchSource().from(0).size(pageSize)
      .query(getQuery(ctx, request.getTenantId(), pageSize, isBrowsingForward))
      .sort(scriptSort(script, STRING).order(isBrowsingForward ? ASC : DESC));

    if (isFalse(request.getExpandAll())) {
      var includes = searchFieldProvider.getSourceFields(request.getResource()).toArray(String[]::new);
      searchSource.fetchSource(includes, null);
    }

    return searchSource;
  }

  private QueryBuilder getQuery(BrowseContext ctx, String tenantId, int size, boolean isBrowsingForward) {
    var anchor = ctx.getAnchor();
    var callNumberAsLong = callNumberTermConverter.convert(anchor);
    var rangeQuery = rangeQuery(CALL_NUMBER_RANGE_FIELD);
    rangeQuery = isBrowsingForward ? rangeQuery.gte(callNumberAsLong) : rangeQuery.lte(callNumberAsLong);

    if (queryConfiguration.isCallNumberBrowseOptimizationEnabled()) {
      var boundary = callNumberBrowseRangeService
        .getRangeBoundaryForBrowsing(tenantId, anchor, size, isBrowsingForward)
        .orElse(null);
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
    return "def f=doc['items.effectiveShelvingOrder'];"
      + "def a=Collections.binarySearch(f,params['cn']);"
      + "if(a>=0) return f[a];a=-a-" + (isBrowsingForward ? "1" : "2")
      + ";f[(int)Math.min(Math.max(0, a),f.length-1)]";
  }
}
