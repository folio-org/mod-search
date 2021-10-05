package org.folio.search.cql.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.MultiMatchQueryBuilder.Type.CROSS_FIELDS;
import static org.elasticsearch.index.query.Operator.AND;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;

import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class EqualTermQueryBuilderTest {

  private final TermQueryBuilder queryBuilder = new EqualTermQueryBuilder();

  @Test
  void getQuery_positive() {
    var actual = queryBuilder.getQuery("value", "f1.*", "f2");
    assertThat(actual).isEqualTo(multiMatchQuery("value", "f1.*", "f2").operator(AND).type(CROSS_FIELDS));
  }

  @Test
  void getFullTextQuery_positive() {
    var actual = queryBuilder.getMultilangQuery("val", "field");
    assertThat(actual).isEqualTo(multiMatchQuery("val", "field.*").operator(AND).type(CROSS_FIELDS));
  }

  @Test
  void getTermLevelQuery_positive() {
    var actual = queryBuilder.getTermLevelQuery("termValue", "field");
    assertThat(actual).isEqualTo(matchQuery("field", "termValue").operator(AND));
  }

  @Test
  void getTermLevelQuery_positive_checkIfFieldExists() {
    var actual = queryBuilder.getTermLevelQuery("", "field");
    assertThat(actual).isEqualTo(existsQuery("field"));
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactly("=");
  }
}
