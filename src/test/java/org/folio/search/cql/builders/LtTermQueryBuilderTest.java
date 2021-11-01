package org.folio.search.cql.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;

import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class LtTermQueryBuilderTest {

  private final LtTermQueryBuilder queryBuilder = new LtTermQueryBuilder();

  @Test
  void getQuery_positive() {
    assertThatThrownBy(() -> queryBuilder.getQuery("value", RESOURCE_NAME, "f1.*", "f2"))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Query is not supported yet [operator(s): [<], field(s): [f1.*, f2]]");
  }

  @Test
  void getFulltextQuery_positive() {
    assertThatThrownBy(() -> queryBuilder.getFulltextQuery("val", "field", RESOURCE_NAME))
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
