package org.folio.search.cql.builders;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestConstants.EMPTY_TERM_MODIFIERS;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;

import java.util.List;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class GtTermQueryBuilderTest {

  private final GtTermQueryBuilder queryBuilder = new GtTermQueryBuilder();

  @Test
  void getQuery_positive_singleField() {
    var actual = queryBuilder.getQuery("value", ResourceType.UNKNOWN, EMPTY_TERM_MODIFIERS, "field");
    assertThat(actual).isEqualTo(rangeQuery("field").gt("value"));
  }

  @Test
  void getQuery_positive() {
    assertThatThrownBy(() -> queryBuilder.getQuery("value", ResourceType.UNKNOWN, EMPTY_TERM_MODIFIERS, "f1.*", "f2"))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Query is not supported yet [operator(s): [>], field(s): [f1.*, f2]]");
  }

  @Test
  void getFulltextQuery_positive() {
    List<String> modifiers = emptyList();
    assertThatThrownBy(() -> queryBuilder.getFulltextQuery("val", "field", ResourceType.UNKNOWN, modifiers))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Query is not supported yet [operator(s): [>], field(s): [field]]");
  }

  @Test
  void getTermLevelQuery_positive() {
    var actual = queryBuilder.getTermLevelQuery("termValue", "field", ResourceType.UNKNOWN, null);
    assertThat(actual).isEqualTo(rangeQuery("field").gt("termValue"));
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactly(">");
  }
}
