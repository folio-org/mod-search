package org.folio.search.cql;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@UnitTest
class ItemSearchFieldModifierTest {

  private ItemSearchFieldModifier itemSearchFieldModifier;

  @BeforeEach
  void setUp() {
    itemSearchFieldModifier = new ItemSearchFieldModifier();
  }

  @Test
  void getSearchTerm_positive() {
    var field = "item.field";
    var expected = "items.field";
    var actual = itemSearchFieldModifier.modify(field);
    assertThat(actual).isEqualTo(expected);
  }
}
