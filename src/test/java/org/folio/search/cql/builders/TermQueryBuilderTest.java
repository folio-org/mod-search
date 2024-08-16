package org.folio.search.cql.builders;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestConstants.EMPTY_TERM_MODIFIERS;

import java.util.Set;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class TermQueryBuilderTest {

  private final TermQueryBuilder queryBuilder = () -> Set.of("op");

  @Test
  void getQuery_positive() {
    assertThatThrownBy(() -> queryBuilder.getQuery("value", ResourceType.UNKNOWN, EMPTY_TERM_MODIFIERS, "f1.*", "f2"))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Query is not supported yet [operator(s): [op], field(s): [f1.*, f2]]");
  }

  @Test
  void getFulltextQuery_positive() {
    assertThatThrownBy(() -> queryBuilder.getFulltextQuery("val", "field", ResourceType.UNKNOWN, emptyList()))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Query is not supported yet [operator(s): [op], field(s): [field]]");
  }

  @Test
  void getTermLevelQuery_positive() {
    assertThatThrownBy(() -> queryBuilder.getTermLevelQuery("val", "field", ResourceType.UNKNOWN, null))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Query is not supported yet [operator(s): [op], field(s): [field]]");
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactly("op");
  }
}
