package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.elasticsearch.index.query.QueryBuilder;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
}
