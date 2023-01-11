package org.folio.search.service.setter.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ItemNormalizedCallNumbersProcessorTest {
  private final ItemNormalizedCallNumbersProcessor processor = new ItemNormalizedCallNumbersProcessor();

  @Test
  void canGetFieldValue_multipleItems() {
    var items = List.of(itemWithCallNumber("Rare Books", "S537.N56 C82", "++"),
      itemWithCallNumber("Oversize", "ABC123.1 .R15 2018", "Curriculum Materials Collection"));

    assertThat(processor.getFieldValue(new Instance().items(items)))
      .containsExactlyInAnyOrder("rarebookss537n56c82", "s537n56c82",
        "oversizeabc1231r152018curriculummaterialscollection", "abc1231r152018curriculummaterialscollection");
  }

  @Test
  void canGetFieldValue_someComponentsAreNulls() {
    var items = List.of(
      itemWithCallNumber(null, "cn1", "suffix1"),
      itemWithCallNumber("prefix2", "cn2", null),
      itemWithCallNumber(null, "cn3", null));

    assertThat(processor.getFieldValue(new Instance().items(items)))
      .containsExactlyInAnyOrder("cn1suffix1", "prefix2cn2", "cn2", "cn3");
  }

  @Test
  void shouldReturnEmptySetWhenNoItems() {
    assertThat(processor.getFieldValue(new Instance())).isEmpty();
  }

  @Test
  void shouldReturnEmptySetWhenItemsHasNoCallNumber() {
    var items = List.of(new Item(), new Item());
    assertThat(processor.getFieldValue(new Instance().items(items))).isEmpty();
  }

  private Item itemWithCallNumber(String prefix, String cn, String suffix) {
    return new Item().effectiveCallNumberComponents(
      new ItemEffectiveCallNumberComponents().prefix(prefix).callNumber(cn).suffix(suffix));
  }
}
