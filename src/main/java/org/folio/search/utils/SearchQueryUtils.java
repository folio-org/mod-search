package org.folio.search.utils;

import static java.util.Locale.ROOT;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude;
import org.elasticsearch.search.builder.SearchSourceBuilder;

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

  /**
   * Creates subject count query from given subject list.
   *
   * @param subjects - list with subjects
   * @return search source for subject counting
   */
  public static SearchSourceBuilder getSubjectCountsQuery(Collection<String> subjects) {
    var lowercaseSubjects = subjects.stream().map(subject -> subject.toLowerCase(ROOT)).toArray(String[]::new);
    var aggregation = AggregationBuilders.terms(SearchUtils.SUBJECT_AGGREGATION_NAME)
      .size(subjects.size()).field(SearchUtils.getPathToFulltextPlainValue(SearchUtils.SUBJECT_AGGREGATION_NAME))
      .includeExclude(new IncludeExclude(lowercaseSubjects, null));
    return searchSource().query(matchAllQuery()).size(0).from(0).aggregation(aggregation);
  }


  /**
   * Backward compatibility
   * Used to rename item to items search field
   *
   * @param index - field name searched for
   * @return field name with replaced items name
   */
  public static String getIndexAndReplaceItem(String index) {
    if (index.startsWith("item.")) {
      return index.replace("item.", "items.");
    }
    return index;
  }
}
