package org.folio.search.cql.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class GtTermQueryBuilderTest {

  private final TermQueryBuilder queryBuilder = new GtTermQueryBuilder();

  @Test
  void getQuery_positive() {
    assertThatThrownBy(() -> queryBuilder.getQuery("value", "f1.*", "f2"))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Query is not supported yet [operator(s): [>], field(s): [f1.*, f2]]");
  }

  @Test
  void getFullTextQuery_positive() {
    assertThatThrownBy(() -> queryBuilder.getMultilangQuery("val", "field"))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Query is not supported yet [operator(s): [>], field(s): [field]]");
  }

  @Test
  void getTermLevelQuery_positive() {
    var actual = queryBuilder.getTermLevelQuery("termValue", "field");
    assertThat(actual).isEqualTo(rangeQuery("field").gt("termValue"));
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactly(">");
  }
}
