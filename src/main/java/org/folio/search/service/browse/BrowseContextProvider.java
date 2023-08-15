package org.folio.search.service.browse;

import static java.util.Collections.emptyList;
import static org.folio.search.utils.CollectionUtils.allMatch;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.folio.search.utils.SearchQueryUtils.isBoolQuery;
import static org.hibernate.internal.util.collections.CollectionHelper.isNotEmpty;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.model.service.BrowseRequest;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.RangeQueryBuilder;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class BrowseContextProvider {

  private static final String QUERY_ERROR_PARAM = "query";
  private final CqlSearchQueryConverter cqlSearchQueryConverter;

  /**
   * Provides {@link BrowseContext} object, created from {@link BrowseRequest} value.
   *
   * @param request - browse request as {@link BrowseRequest} object
   * @return created {@link  BrowseContext} object
   * @throws RequestValidationException if given {@link QueryBuilder} does not satisfy required conditions
   */
  public BrowseContext get(BrowseRequest request) {
    log.debug("get:: by [query: {}, resource: {}]", request.getQuery(), request.getResource());

    // todo(MSEARCH-580): use 'convertForConsortia' or/and check todo item for 'convertForConsortia'
    var searchSource = cqlSearchQueryConverter.convert(request.getQuery(), request.getResource());
    var cqlQuery = request.getQuery();
    if (isNotEmpty(searchSource.sorts())) {
      throw new RequestValidationException(
        "Invalid CQL query for browsing, 'sortBy' is not supported", QUERY_ERROR_PARAM, cqlQuery);
    }

    var query = searchSource.query();
    if (!isBoolQuery(query)) {
      if (!isValidRangeQuery(request.getTargetField(), request.getSubField(), query)) {
        throw new RequestValidationException("Invalid CQL query for browsing.", QUERY_ERROR_PARAM, cqlQuery);
      }
      log.trace(
        "Failure on passing BoolQueryBuilder. Attempts to create browsingContext without filters [request: {}]",
        request);
      return createBrowsingContext(request, emptyList(), (RangeQueryBuilder) query);
    }

    var boolQuery = (BoolQueryBuilder) query;
    var filters = boolQuery.filter();
    var shouldClauses = boolQuery.should();
    String logMsg = collectionToLogMsg(filters, true);

    if (isValidAroundQuery(request.getTargetField(), request.getSubField(), shouldClauses)) {
      log.trace("Attempts to create context browsingAround [request: {}, filters.size: {}]",
        request, logMsg);
      return createContextForBrowsingAround(request, filters, shouldClauses);
    }

    if (isBoolQueryWithFilters(boolQuery)) {
      var mustClauses = boolQuery.must();
      var firstMustClause = mustClauses.get(0);
      if (firstMustClause instanceof RangeQueryBuilder rangeQuery) {
        log.trace("Attempts to create browsingContext with filters [request: {}, filters.size: {}]",
          request, logMsg);
        return createBrowsingContext(request, filters, rangeQuery);
      }

      if (isBoolQuery(firstMustClause)) {
        var subShouldClauses = ((BoolQueryBuilder) firstMustClause).should();
        if (isValidAroundQuery(request.getTargetField(), request.getSubField(), subShouldClauses)) {
          log.trace("Attempts to create context browsingAround with filters [request: {}, filters: {}]",
            request, logMsg);
          return createContextForBrowsingAround(request, filters, subShouldClauses);
        }
      }
    }

    throw new RequestValidationException("Invalid CQL query for browsing.", QUERY_ERROR_PARAM, cqlQuery);
  }

  private static BrowseContext createBrowsingContext(BrowseRequest request, List<QueryBuilder> filters,
                                                     RangeQueryBuilder rangeQuery) {
    var precedingQuery = getRangeQuery(rangeQuery, query -> query.to() != null);
    var succeedingQuery = getRangeQuery(rangeQuery, query -> query.from() != null);

    return BrowseContext.builder()
      .precedingQuery(precedingQuery)
      .succeedingQuery(succeedingQuery)
      .filters(filters)
      .anchor(getAnchor(rangeQuery))
      .precedingLimit(precedingQuery != null ? request.getLimit() : null)
      .succeedingLimit(succeedingQuery != null ? request.getLimit() : null)
      .build();
  }

  private static BrowseContext createContextForBrowsingAround(
    BrowseRequest request, List<QueryBuilder> filters, List<QueryBuilder> shouldClauses) {
    var precedingQuery = getRangeQuery(shouldClauses, query -> query.to() != null);
    var succeedingQuery = getRangeQuery(shouldClauses, query -> query.from() != null);

    return BrowseContext.builder()
      .precedingQuery(precedingQuery)
      .succeedingQuery(succeedingQuery)
      .filters(filters)
      .anchor(validateAndGetAnchorForBrowsingAround(request, shouldClauses))
      .precedingLimit(request.getPrecedingRecordsCount())
      .succeedingLimit(request.getLimit() - request.getPrecedingRecordsCount())
      .build();
  }

  private static RangeQueryBuilder getRangeQuery(RangeQueryBuilder query, Predicate<RangeQueryBuilder> predicate) {
    return predicate.test(query) ? query : null;
  }

  private static RangeQueryBuilder getRangeQuery(List<QueryBuilder> queries, Predicate<RangeQueryBuilder> predicate) {
    var firstQuery = (RangeQueryBuilder) queries.get(0);
    return predicate.test(firstQuery) ? firstQuery : (RangeQueryBuilder) queries.get(1);
  }

  static boolean isValidRangeQuery(String targetField, String subField, QueryBuilder q) {
    if (q instanceof RangeQueryBuilder rangeQuery) {
      var fieldName = rangeQuery.fieldName();
      var isTargetValid = targetField.equals(fieldName);
      if (!isTargetValid && subField != null) {
        return subField.equals(fieldName);
      }
      return isTargetValid;
    }
    log.warn("isValidRangeQuery:: not valid range");
    return false;
  }

  private static boolean isValidAroundQuery(String targetField, String subField, List<QueryBuilder> queries) {
    if (queries.size() == 2 && allMatch(queries, query -> isValidRangeQuery(targetField, subField, query))) {
      log.debug("isValidAroundQuery:: queries.size() == 2 && allMatch by queries");
      var firstClause = (RangeQueryBuilder) queries.get(0);
      var secondClause = (RangeQueryBuilder) queries.get(1);
      return firstClause.from() != null && secondClause.from() == null
        || firstClause.from() == null && secondClause.from() != null;
    }

    log.warn("isValidAroundQuery:: not valid query");
    return false;
  }

  private static boolean isBoolQueryWithFilters(BoolQueryBuilder boolQuery) {
    return boolQuery.must().size() == 1 && !boolQuery.filter().isEmpty();
  }

  private static String getAnchor(RangeQueryBuilder rangeQuery) {
    return rangeQuery.from() != null ? (String) rangeQuery.from() : (String) rangeQuery.to();
  }

  private static String validateAndGetAnchorForBrowsingAround(BrowseRequest request, List<QueryBuilder> shouldClauses) {
    var firstAnchor = getAnchor((RangeQueryBuilder) shouldClauses.get(0));
    var secondAnchor = getAnchor((RangeQueryBuilder) shouldClauses.get(1));

    if (!Objects.equals(firstAnchor, secondAnchor)) {
      throw new RequestValidationException(
        "Invalid CQL query for browsing. Anchors must be the same in range conditions.",
        QUERY_ERROR_PARAM, request.getQuery());
    }

    return firstAnchor;
  }
}
