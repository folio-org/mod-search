package org.folio.search.cql.builders;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestConstants.EMPTY_TERM_MODIFIERS;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;

import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class LtTermQueryBuilderTest {

  private final LtTermQueryBuilder queryBuilder = new LtTermQueryBuilder();

  @Test
  void getQuery_positive_singleField() {
    var actual = queryBuilder.getQuery("value", RESOURCE_NAME, EMPTY_TERM_MODIFIERS, "field");
    assertThat(actual).isEqualTo(rangeQuery("field").lt("value"));
  }

  @Test
  void getQuery_positive() {
    assertThatThrownBy(() -> queryBuilder.getQuery("value", RESOURCE_NAME, EMPTY_TERM_MODIFIERS, "f1.*", "f2"))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Query is not supported yet [operator(s): [<], field(s): [f1.*, f2]]");
  }

  @Test
  void getFulltextQuery_positive() {
    assertThatThrownBy(() -> queryBuilder.getFulltextQuery("val", "field", RESOURCE_NAME, emptyList()))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Query is not supported yet [operator(s): [<], field(s): [field]]");
  }

  @Test
  void getTermLevelQuery_positive() {
    var actual = queryBuilder.getTermLevelQuery("termValue", "field", RESOURCE_NAME, null);
    assertThat(actual).isEqualTo(rangeQuery("field").lt("termValue"));
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactly("<");
  }
}
