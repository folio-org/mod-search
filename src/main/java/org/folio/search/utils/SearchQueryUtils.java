package org.folio.search.utils;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import java.util.Objects;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
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
   * @param filterFieldPredicate predicate to check if subquery is filter query or not.
   * @return true if query is disjunction filter query, false - otherwise
   */
  public static boolean isDisjunctionFilterQuery(QueryBuilder query, Predicate<String> filterFieldPredicate) {
    if (!isBoolQuery(query)) {
      return false;
    }
    var boolQuery = (BoolQueryBuilder) query;
    if (isEmpty(boolQuery.should()) || isNotEmpty(boolQuery.must()) || isNotEmpty(boolQuery.mustNot())) {
      return false;
    }

    String baseFieldName = null;
    for (var innerQuery : boolQuery.should()) {
      if (!isFilterQuery(innerQuery, filterFieldPredicate)) {
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

  private static boolean isFilterQuery(QueryBuilder query, Predicate<String> filterFieldPredicate) {
    return (query instanceof TermQueryBuilder) && filterFieldPredicate.test(((TermQueryBuilder) query).fieldName());
  }
}
