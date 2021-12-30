package org.folio.search.model.service;

import static java.lang.Math.max;
import static java.util.Collections.emptyList;
import static org.folio.search.service.CallNumberBrowseService.CALL_NUMBER_BROWSING_FIELD;
import static org.folio.search.utils.CollectionUtils.allMatch;
import static org.folio.search.utils.SearchQueryUtils.isBoolQuery;
import static org.hibernate.internal.util.collections.CollectionHelper.isNotEmpty;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.Data;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.exception.RequestValidationException;

@Data
public class CallNumberServiceContext {

  private final RangeQueryBuilder precedingQuery;
  private final RangeQueryBuilder succeedingQuery;
  private final List<QueryBuilder> filters;
  private final long anchor;

  private final Integer precedingLimit;
  private final Integer succeedingLimit;

  /**
   * Checks if created {@link CallNumberServiceContext} is purposed for browsing around.
   *
   * @return true - if context is purposed for browsing around
   */
  public boolean isBrowsingAround() {
    return this.precedingQuery != null && this.succeedingQuery != null;
  }

  /**
   * Checks if created {@link CallNumberServiceContext} is purposed for browsing forward.
   *
   * @return true - if context is purposed for browsing forward
   */
  public boolean isForwardBrowsing() {
    return this.succeedingQuery != null;
  }

  /**
   * Returns limit for call-number browsing.
   */
  public int getLimit(boolean isForward) {
    return isForward ? this.succeedingLimit : this.precedingLimit;
  }

  /**
   * Updates range queries in {@link  CallNumberServiceContext} adding customizable by tenant offset to increase
   * performance.
   *
   * @param offset - offset to limit amount of result found by call-number browse queries
   * @return {@link CallNumberServiceContext} with updated range quiries
   */
  public CallNumberServiceContext withUpdatedRanges(long offset) {
    if (this.precedingQuery != null) {
      this.precedingQuery.gte(max(this.anchor - offset, 0L));
    }
    if (this.succeedingQuery != null) {
      this.succeedingQuery.lte(this.anchor + offset < 0 ? Long.MAX_VALUE : this.anchor + offset);
    }
    return this;
  }

  /**
   * Static factory method to create {@link CallNumberServiceContext} object for given {@link CallNumberBrowseRequest}
   * object and Elasticsearch search source.
   *
   * @param request - call number browse request as {@link CallNumberBrowseRequest} object
   * @param searchSource - Elasticsearch search source as {@link SearchSourceBuilder} object
   * @return created {@link  CallNumberServiceContext} object
   * @throws RequestValidationException if given {@link QueryBuilder} does not satisfy required conditions
   */
  public static CallNumberServiceContext of(CallNumberBrowseRequest request, SearchSourceBuilder searchSource) {
    var cqlQuery = request.getQuery();
    if (isNotEmpty(searchSource.sorts())) {
      throw new RequestValidationException(
        "Invalid CQL query for call-number browsing, 'sortBy' is not supported", "query", cqlQuery);
    }

    var query = searchSource.query();
    if (!isBoolQuery(query)) {
      if (!isValidRangeQuery(query)) {
        throw new RequestValidationException("Invalid CQL query for call-number browsing.", "query", cqlQuery);
      }
      return createBrowsingContext(request, emptyList(), (RangeQueryBuilder) query);
    }

    var boolQuery = (BoolQueryBuilder) query;
    var filters = boolQuery.filter();
    var shouldClauses = boolQuery.should();

    if (isValidAroundQuery(shouldClauses)) {
      return createContextForBrowsingAround(request, filters, shouldClauses);
    }

    if (isBoolQueryWithFilters(boolQuery)) {
      var mustClauses = boolQuery.must();
      var firstMustClause = mustClauses.get(0);
      if (firstMustClause instanceof RangeQueryBuilder) {
        return createBrowsingContext(request, filters, (RangeQueryBuilder) firstMustClause);
      }

      if (isBoolQuery(firstMustClause)) {
        var subShouldClauses = ((BoolQueryBuilder) firstMustClause).should();
        if (isValidAroundQuery(subShouldClauses)) {
          return createContextForBrowsingAround(request, filters, subShouldClauses);
        }
      }
    }

    throw new RequestValidationException("Invalid CQL query for call-number browsing.", "query", cqlQuery);
  }

  private static CallNumberServiceContext createBrowsingContext(
    CallNumberBrowseRequest request, List<QueryBuilder> filters, RangeQueryBuilder rangeQuery) {
    var precedingQuery = getRangeQuery(rangeQuery, query -> query.to() != null);
    var succeedingQuery = getRangeQuery(rangeQuery, query -> query.from() != null);
    var limit = request.getLimit();
    var precedingLimit = precedingQuery != null ? limit : null;
    var succeedingLimit = succeedingQuery != null ? limit : null;

    return new CallNumberServiceContext(precedingQuery, succeedingQuery, filters, getAnchor(rangeQuery),
      precedingLimit, succeedingLimit);
  }

  private static CallNumberServiceContext createContextForBrowsingAround(
    CallNumberBrowseRequest request, List<QueryBuilder> filters, List<QueryBuilder> shouldClauses) {
    var precedingQuery = getRangeQuery(shouldClauses, query -> query.to() != null);
    var succeedingQuery = getRangeQuery(shouldClauses, query -> query.from() != null);
    var firstAnchor = getAnchor((RangeQueryBuilder) shouldClauses.get(0));
    var secondAnchor = getAnchor((RangeQueryBuilder) shouldClauses.get(1));

    if (!Objects.equals(firstAnchor, secondAnchor)) {
      throw new RequestValidationException(
        "Invalid CQL query for call-number browsing. Anchors must be the same in range conditions.",
        "query", request.getQuery());
    }

    var precedingRecordsCount = request.getPrecedingRecordsCount();
    return new CallNumberServiceContext(precedingQuery, succeedingQuery, filters, firstAnchor,
      precedingRecordsCount, request.getLimit() - precedingRecordsCount);
  }

  private static RangeQueryBuilder getRangeQuery(RangeQueryBuilder query, Predicate<RangeQueryBuilder> predicate) {
    return predicate.test(query) ? query : null;
  }

  private static RangeQueryBuilder getRangeQuery(List<QueryBuilder> queries, Predicate<RangeQueryBuilder> predicate) {
    var firstQuery = (RangeQueryBuilder) queries.get(0);
    return predicate.test(firstQuery) ? firstQuery : (RangeQueryBuilder) queries.get(1);
  }

  static boolean isValidRangeQuery(QueryBuilder q) {
    return q instanceof RangeQueryBuilder && CALL_NUMBER_BROWSING_FIELD.equals(((RangeQueryBuilder) q).fieldName());
  }

  private static boolean isValidAroundQuery(List<QueryBuilder> queries) {
    return queries.size() == 2 && allMatch(queries, CallNumberServiceContext::isValidRangeQuery);
  }

  private static boolean isBoolQueryWithFilters(BoolQueryBuilder boolQuery) {
    return boolQuery.must().size() == 1 && !boolQuery.filter().isEmpty();
  }

  private static Long getAnchor(RangeQueryBuilder rangeQuery) {
    return rangeQuery.from() != null ? (Long) rangeQuery.from() : (Long) rangeQuery.to();
  }
}
