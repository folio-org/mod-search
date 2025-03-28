package org.folio.search.cql;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ItemSearchFieldModifierTest {

  private final ItemSearchFieldModifier itemSearchFieldModifier = new ItemSearchFieldModifier();

  @Test
  void getSearchTerm_positive() {
    var field = "item.field";
    var expected = "items.field";
    var actual = itemSearchFieldModifier.modify(field);
    assertThat(actual).isEqualTo(expected);
  }
}
