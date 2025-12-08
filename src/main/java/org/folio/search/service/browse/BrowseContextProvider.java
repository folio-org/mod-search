package org.folio.search.service.browse;

import static java.util.Collections.emptyList;
import static org.folio.search.utils.CollectionUtils.allMatch;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.folio.search.utils.SearchQueryUtils.isBoolQuery;
import static org.hibernate.internal.util.collections.CollectionHelper.isNotEmpty;

import java.util.List;
import java.util.Objects;
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
  private static final int EXPECTED_AROUND_QUERY_SIZE = 2;
  private static final int FIRST_MUST_CLAUSE_INDEX = 0;

  private final CqlSearchQueryConverter cqlSearchQueryConverter;

  /**
   * Provides {@link BrowseContext} object, created from {@link BrowseRequest} value.
   *
   * @param request - browse request as {@link BrowseRequest} object
   * @return created {@link  BrowseContext} object
   * @throws RequestValidationException if given {@link QueryBuilder} does not satisfy required conditions
   */
  public BrowseContext get(BrowseRequest request) {
    log.debug("get:: by [query: {}, resource: {}]", request.query(), request.resource());

    var searchSource = cqlSearchQueryConverter.convert(request.query(), request.resource());
    var cqlQuery = request.query();

    validateNoSortingPresent(searchSource.sorts(), cqlQuery);

    var query = searchSource.query();

    if (!isBoolQuery(query)) {
      return handleSimpleRangeQuery(request, query, cqlQuery);
    }

    return handleBoolQuery(request, (BoolQueryBuilder) query, cqlQuery);
  }

  private void validateNoSortingPresent(List<?> sorts, String cqlQuery) {
    if (isNotEmpty(sorts)) {
      throw new RequestValidationException(
        "Invalid CQL query for browsing, 'sortBy' is not supported", QUERY_ERROR_PARAM, cqlQuery);
    }
  }

  private BrowseContext handleSimpleRangeQuery(BrowseRequest request, QueryBuilder query, String cqlQuery) {
    if (!isValidRangeQuery(request.targetField(), query)) {
      throw new RequestValidationException("Invalid CQL query for browsing.", QUERY_ERROR_PARAM, cqlQuery);
    }

    log.trace("Creating browsingContext without filters [request: {}]", request);
    return buildBrowseContext(request, emptyList(), (RangeQueryBuilder) query);
  }

  private BrowseContext handleBoolQuery(BrowseRequest request, BoolQueryBuilder boolQuery, String cqlQuery) {
    var filters = boolQuery.filter();
    var shouldClauses = boolQuery.should();
    String logMsg = collectionToLogMsg(filters, true);

    if (isValidAroundQuery(request.targetField(), shouldClauses)) {
      log.trace("Creating context browsingAround [request: {}, filters.size: {}]", request, logMsg);
      return buildBrowseAroundContext(request, filters, shouldClauses);
    }

    if (hasSingleMustClauseWithFilters(boolQuery)) {
      var firstMustClause = boolQuery.must().get(FIRST_MUST_CLAUSE_INDEX);

      if (firstMustClause instanceof RangeQueryBuilder rangeQuery) {
        log.trace("Creating browsingContext with filters [request: {}, filters.size: {}]", request, logMsg);
        return buildBrowseContext(request, filters, rangeQuery);
      }

      if (isBoolQuery(firstMustClause)) {
        var nestedShouldClauses = ((BoolQueryBuilder) firstMustClause).should();
        if (isValidAroundQuery(request.targetField(), nestedShouldClauses)) {
          log.trace("Creating context browsingAround with filters [request: {}, filters: {}]", request, logMsg);
          return buildBrowseAroundContext(request, filters, nestedShouldClauses);
        }
      }
    }

    throw new RequestValidationException("Invalid CQL query for browsing.", QUERY_ERROR_PARAM, cqlQuery);
  }

  private static boolean isValidRangeQuery(String targetField, QueryBuilder q) {
    if (q instanceof RangeQueryBuilder rangeQuery) {
      return targetField.equals(rangeQuery.fieldName());
    }
    log.warn("isValidRangeQuery:: not a valid range query");
    return false;
  }

  private static boolean hasSingleMustClauseWithFilters(BoolQueryBuilder boolQuery) {
    return boolQuery.must().size() == 1 && !boolQuery.filter().isEmpty();
  }

  private static BrowseContext buildBrowseContext(BrowseRequest request, List<QueryBuilder> filters,
                                                  RangeQueryBuilder rangeQuery) {
    var precedingQuery = extractPrecedingQuery(rangeQuery);
    var succeedingQuery = extractSucceedingQuery(rangeQuery);

    return BrowseContext.builder()
      .precedingQuery(precedingQuery)
      .succeedingQuery(succeedingQuery)
      .filters(filters)
      .anchor(extractAnchor(rangeQuery))
      .precedingLimit(precedingQuery != null ? request.limit() : null)
      .succeedingLimit(succeedingQuery != null ? request.limit() : null)
      .build();
  }

  private static BrowseContext buildBrowseAroundContext(BrowseRequest request, List<QueryBuilder> filters,
                                                        List<QueryBuilder> shouldClauses) {
    var precedingQuery = extractPrecedingQuery(shouldClauses);
    var succeedingQuery = extractSucceedingQuery(shouldClauses);

    return BrowseContext.builder()
      .precedingQuery(precedingQuery)
      .succeedingQuery(succeedingQuery)
      .filters(filters)
      .anchor(validateAndExtractAnchorForBrowsingAround(request, shouldClauses))
      .precedingLimit(request.precedingRecordsCount())
      .succeedingLimit(request.limit() - request.precedingRecordsCount())
      .build();
  }

  private static RangeQueryBuilder extractPrecedingQuery(RangeQueryBuilder query) {
    return hasToValue(query) ? query : null;
  }

  private static RangeQueryBuilder extractPrecedingQuery(List<QueryBuilder> queries) {
    var firstQuery = (RangeQueryBuilder) queries.get(0);
    return hasToValue(firstQuery) ? firstQuery : (RangeQueryBuilder) queries.get(1);
  }

  private static RangeQueryBuilder extractSucceedingQuery(RangeQueryBuilder query) {
    return hasFromValue(query) ? query : null;
  }

  private static RangeQueryBuilder extractSucceedingQuery(List<QueryBuilder> queries) {
    var firstQuery = (RangeQueryBuilder) queries.get(0);
    return hasFromValue(firstQuery) ? firstQuery : (RangeQueryBuilder) queries.get(1);
  }

  private static boolean hasToValue(RangeQueryBuilder query) {
    return query.to() != null;
  }

  private static boolean hasFromValue(RangeQueryBuilder query) {
    return query.from() != null;
  }

  private static boolean isValidAroundQuery(String targetField, List<QueryBuilder> queries) {
    if (queries.size() != EXPECTED_AROUND_QUERY_SIZE
        || !allMatch(queries, query -> isValidRangeQuery(targetField, query))) {
      log.warn("isValidAroundQuery:: invalid query structure");
      return false;
    }

    var firstClause = (RangeQueryBuilder) queries.get(0);
    var secondClause = (RangeQueryBuilder) queries.get(1);

    boolean hasValidDirections = hasFromValue(firstClause) && !hasFromValue(secondClause)
                                 || !hasFromValue(firstClause) && hasFromValue(secondClause);

    if (hasValidDirections) {
      log.debug("isValidAroundQuery:: valid around query with opposite directions");
    }

    return hasValidDirections;
  }

  private static String extractAnchor(RangeQueryBuilder rangeQuery) {
    return rangeQuery.from() != null ? (String) rangeQuery.from() : (String) rangeQuery.to();
  }

  private static String validateAndExtractAnchorForBrowsingAround(BrowseRequest request,
                                                                  List<QueryBuilder> shouldClauses) {
    var firstAnchor = extractAnchor((RangeQueryBuilder) shouldClauses.get(0));
    var secondAnchor = extractAnchor((RangeQueryBuilder) shouldClauses.get(1));

    if (!Objects.equals(firstAnchor, secondAnchor)) {
      throw new RequestValidationException(
        "Invalid CQL query for browsing. Anchors must be the same in range conditions.",
        QUERY_ERROR_PARAM, request.query());
    }

    return firstAnchor;
  }
}
