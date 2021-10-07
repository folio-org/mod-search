package org.folio.search.cql.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class TermQueryBuilderTest {

  private final TermQueryBuilder queryBuilder = () -> Set.of("op");

  @Test
  void getQuery_positive() {
    assertThatThrownBy(() -> queryBuilder.getQuery("value", "f1.*", "f2"))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Query is not supported yet [operator(s): [op], field(s): [f1.*, f2]]");
  }

  @Test
  void getMultilangQuery_positive() {
    assertThatThrownBy(() -> queryBuilder.getMultilangQuery("val", "field"))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Query is not supported yet [operator(s): [op], field(s): [field]]");
  }

  @Test
  void getTermLevelQuery_positive() {
    assertThatThrownBy(() -> queryBuilder.getTermLevelQuery("val", "field", null))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Query is not supported yet [operator(s): [op], field(s): [field]]");
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactly("op");
  }
}
