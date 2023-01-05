package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchQuery;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.index.query.QueryBuilders.termsQuery;
import static org.opensearch.search.aggregations.AggregationBuilders.terms;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;

import java.util.List;
import java.util.stream.Stream;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.aggregations.bucket.terms.IncludeExclude;

@UnitTest
class SearchQueryUtilsTest {

  private static final String FIELD = "field";

  @Test
  void isBoolQuery_positive() {
    var actual = SearchQueryUtils.isBoolQuery(boolQuery());
    assertThat(actual).isTrue();
  }

  @Test
  void isBoolQuery_negative() {
    var actual = SearchQueryUtils.isBoolQuery(termQuery(FIELD, "value"));
    assertThat(actual).isFalse();
  }

  @DisplayName("isDisjunctionFilterQuery_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}, expected={1}")
  @MethodSource("isDisjunctionFilterQueryDataProvider")
  void isDisjunctionFilterQuery_parameterized(QueryBuilder queryBuilder, boolean expected) {
    var actual = SearchQueryUtils.isDisjunctionFilterQuery(queryBuilder, FIELD::equals);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void isDisjunctionFilterQuery_filterByMultipleFields() {
    var queryBuilder = boolQuery()
      .should(termQuery("f1", "v"))
      .should(termQuery("f2", "v"))
      .should(termQuery("f1", "v"));

    var actual = SearchQueryUtils.isDisjunctionFilterQuery(queryBuilder, field -> true);
    assertThat(actual).isFalse();
  }

  @DisplayName("isFilterQuery_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}, expected={1}")
  @MethodSource("isFilterQueryDataProvider")
  void isFilterQuery_parameterized(QueryBuilder queryBuilder, boolean expected) {
    var actual = SearchQueryUtils.isFilterQuery(queryBuilder, FIELD::equals);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void getSubjectCountsQuery_positive() {
    var actual = SearchQueryUtils.getSubjectCountsQuery(List.of("s1", "s2"));
    assertThat(actual).isEqualTo(searchSource().from(0).size(0)
      .query(boolQuery().filter(termsQuery("subjects.plain_value", "s1", "s2")))
      .aggregation(terms("subjects.value").size(2).field("subjects.plain_value")
        .includeExclude(new IncludeExclude(new String[] {"s1", "s2"}, null)))
    );
  }

  private static Stream<Arguments> isDisjunctionFilterQueryDataProvider() {
    var termQuery = termQuery(FIELD, "v");
    return Stream.of(
      arguments(termQuery, false),
      arguments(boolQuery().should(termQuery(FIELD, "v1")).should(termQuery(FIELD, "v2")), true),
      arguments(boolQuery().should(termQuery(FIELD, "v1")).should(termQuery(FIELD, "v2")), true),
      arguments(boolQuery().should(termQuery).should(termQuery("f1", "v"))
        .should(termQuery("f2", "v")), false),
      arguments(boolQuery().should(termQuery(FIELD, "v1")).mustNot(termQuery(FIELD, "v2")), false),
      arguments(boolQuery().must(termQuery), false),
      arguments(boolQuery().must(termQuery).mustNot(termQuery), false),
      arguments(boolQuery().must(termQuery).should(termQuery), false),
      arguments(boolQuery().mustNot(termQuery), false),
      arguments(boolQuery().mustNot(termQuery).should(termQuery), false),
      arguments(boolQuery().mustNot(termQuery).must(termQuery).should(termQuery), false)
    );
  }

  private static Stream<Arguments> isFilterQueryDataProvider() {
    return Stream.of(
      arguments(rangeQuery(FIELD).gt(10), true),
      arguments(rangeQuery("f").gt(10), false),
      arguments(matchQuery(FIELD, "value"), false),
      arguments(termQuery(FIELD, "value"), true),
      arguments(termQuery("f", "v"), false)
    );
  }
}
