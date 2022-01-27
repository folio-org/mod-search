package org.folio.search.model.service;

import static java.util.Collections.emptyList;
import static org.folio.search.utils.CollectionUtils.allMatch;
import static org.folio.search.utils.SearchQueryUtils.isBoolQuery;
import static org.hibernate.internal.util.collections.CollectionHelper.isNotEmpty;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.exception.RequestValidationException;

@Data
@RequiredArgsConstructor
public class BrowseContext {

  private final RangeQueryBuilder precedingQuery;
  private final RangeQueryBuilder succeedingQuery;
  private final List<QueryBuilder> filters;
  private final Object anchor;

  private final Integer precedingLimit;
  private final Integer succeedingLimit;

  /**
   * Checks if created {@link BrowseContext} is purposed for browsing around.
   *
   * @return true - if context is purposed for browsing around
   */
  public boolean isAroundBrowsing() {
    return this.precedingQuery != null && this.succeedingQuery != null;
  }

  /**
   * Checks if created {@link BrowseContext} is purposed for browsing forward.
   *
   * @return true - if context is purposed for browsing forward
   */
  public boolean isForwardBrowsing() {
    return this.succeedingQuery != null;
  }


  /**
   * Checks if anchor is included in the range query or not using browsing direction flag.
   *
   * @return {@code true} if anchor is included, {@code false} - otherwise
   */
  public boolean isAnchorIncluded(boolean isForward) {
    return isForward ? this.succeedingQuery.includeLower() : this.precedingQuery.includeUpper();
  }

  /**
   * Checks if anchor is included in the range query or not.
   *
   * @return {@code true} if anchor is included, {@code false} - otherwise
   */
  public boolean isAnchorIncluded() {
    return isAnchorIncluded(true) || isAnchorIncluded(false);
  }

  /**
   * Returns limit for browsing.
   */
  public int getLimit(boolean isForward) {
    return isForward ? this.succeedingLimit : this.precedingLimit;
  }

  /**
   * Static factory method to create {@link BrowseContext} object for given {@link BrowseRequest} object and
   * Elasticsearch search source.
   *
   * @param request - browse request as {@link BrowseRequest} object
   * @param searchSource - Elasticsearch search source as {@link SearchSourceBuilder} object
   * @return created {@link  BrowseContext} object
   * @throws RequestValidationException if given {@link QueryBuilder} does not satisfy required conditions
   */
  public static BrowseContext of(BrowseRequest request, SearchSourceBuilder searchSource) {
    var cqlQuery = request.getQuery();
    if (isNotEmpty(searchSource.sorts())) {
      throw new RequestValidationException(
        "Invalid CQL query for browsing, 'sortBy' is not supported", "query", cqlQuery);
    }

    var query = searchSource.query();
    if (!isBoolQuery(query)) {
      if (!isValidRangeQuery(request.getTargetField(), query)) {
        throw new RequestValidationException("Invalid CQL query for browsing.", "query", cqlQuery);
      }
      return createBrowsingContext(request, emptyList(), (RangeQueryBuilder) query);
    }

    var boolQuery = (BoolQueryBuilder) query;
    var filters = boolQuery.filter();
    var shouldClauses = boolQuery.should();

    if (isValidAroundQuery(request.getTargetField(), shouldClauses)) {
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
        if (isValidAroundQuery(request.getTargetField(), subShouldClauses)) {
          return createContextForBrowsingAround(request, filters, subShouldClauses);
        }
      }
    }

    throw new RequestValidationException("Invalid CQL query for browsing.", "query", cqlQuery);
  }

  private static BrowseContext createBrowsingContext(
    BrowseRequest request, List<QueryBuilder> filters, RangeQueryBuilder rangeQuery) {
    var precedingQuery = getRangeQuery(rangeQuery, query -> query.to() != null);
    var succeedingQuery = getRangeQuery(rangeQuery, query -> query.from() != null);
    var limit = request.getLimit();
    var precedingLimit = precedingQuery != null ? limit : null;
    var succeedingLimit = succeedingQuery != null ? limit : null;

    return new BrowseContext(precedingQuery, succeedingQuery, filters, getAnchor(rangeQuery),
      precedingLimit, succeedingLimit);
  }

  private static BrowseContext createContextForBrowsingAround(
    BrowseRequest request, List<QueryBuilder> filters, List<QueryBuilder> shouldClauses) {
    var precedingQuery = getRangeQuery(shouldClauses, query -> query.to() != null);
    var succeedingQuery = getRangeQuery(shouldClauses, query -> query.from() != null);
    var firstAnchor = getAnchor((RangeQueryBuilder) shouldClauses.get(0));
    var secondAnchor = getAnchor((RangeQueryBuilder) shouldClauses.get(1));

    if (!Objects.equals(firstAnchor, secondAnchor)) {
      throw new RequestValidationException(
        "Invalid CQL query for browsing. Anchors must be the same in range conditions.",
        "query", request.getQuery());
    }

    var precedingRecordsCount = request.getPrecedingRecordsCount();
    return new BrowseContext(precedingQuery, succeedingQuery, filters, firstAnchor,
      precedingRecordsCount, request.getLimit() - precedingRecordsCount);
  }

  private static RangeQueryBuilder getRangeQuery(RangeQueryBuilder query, Predicate<RangeQueryBuilder> predicate) {
    return predicate.test(query) ? query : null;
  }

  private static RangeQueryBuilder getRangeQuery(List<QueryBuilder> queries, Predicate<RangeQueryBuilder> predicate) {
    var firstQuery = (RangeQueryBuilder) queries.get(0);
    return predicate.test(firstQuery) ? firstQuery : (RangeQueryBuilder) queries.get(1);
  }

  static boolean isValidRangeQuery(String targetField, QueryBuilder q) {
    return q instanceof RangeQueryBuilder && targetField.equals(((RangeQueryBuilder) q).fieldName());
  }

  private static boolean isValidAroundQuery(String targetField, List<QueryBuilder> queries) {
    return queries.size() == 2 && allMatch(queries, query -> isValidRangeQuery(targetField, query));
  }

  private static boolean isBoolQueryWithFilters(BoolQueryBuilder boolQuery) {
    return boolQuery.must().size() == 1 && !boolQuery.filter().isEmpty();
  }

  private static Object getAnchor(RangeQueryBuilder rangeQuery) {
    return rangeQuery.from() != null ? rangeQuery.from() : rangeQuery.to();
  }
}
