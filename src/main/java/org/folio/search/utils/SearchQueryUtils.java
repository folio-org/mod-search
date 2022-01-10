package org.folio.search.utils;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import java.util.Objects;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchQueryUtils {

  /**
   * Checks if passed {@link QueryBuilder} has type of {@link BoolQueryBuilder} or not.
   *
   * @param query query to analyze
   * @return true if passed query is bool query, false - otherwise.
   */
  public static boolean isBoolQuery(QueryBuilder query) {
    return query instanceof BoolQueryBuilder;
  }

  /**
   * Checks if passed query contains set of disjunction filter conditions.
   *
   * @param query query to analyze
   * @param check predicate to check if subquery is filter query or not.
   * @return true if query is disjunction filter query, false - otherwise
   */
  public static boolean isDisjunctionFilterQuery(QueryBuilder query, Predicate<String> check) {
    if (!isBoolQuery(query)) {
      return false;
    }
    var boolQuery = (BoolQueryBuilder) query;
    if (isEmpty(boolQuery.should()) || isNotEmpty(boolQuery.must()) || isNotEmpty(boolQuery.mustNot())) {
      return false;
    }

    String baseFieldName = null;
    for (var innerQuery : boolQuery.should()) {
      if (!isFilterQuery(innerQuery, check)) {
        return false;
      }
      var currentFieldName = ((TermQueryBuilder) innerQuery).fieldName();
      if (baseFieldName == null) {
        baseFieldName = currentFieldName;
      }
      if (!Objects.equals(currentFieldName, baseFieldName)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks if passed query is type of filter or not using given predicate.
   *
   * @param query es query as {@link QueryBuilder} object
   * @param check filter field predicate to test query field
   * @return true if query is type of filter, false - otherwise
   */
  public static boolean isFilterQuery(QueryBuilder query, Predicate<String> check) {
    return query instanceof TermQueryBuilder && check.test(((TermQueryBuilder) query).fieldName())
      || query instanceof RangeQueryBuilder && check.test(((RangeQueryBuilder) query).fieldName());
  }

}
