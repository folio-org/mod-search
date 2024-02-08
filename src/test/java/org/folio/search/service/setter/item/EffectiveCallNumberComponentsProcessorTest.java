package org.folio.search.service.setter.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class EffectiveCallNumberComponentsProcessorTest {
  private final EffectiveCallNumberComponentsProcessor processor = new EffectiveCallNumberComponentsProcessor();

  @Test
  void canGetFieldValue_multipleItems() {
    var items = List.of(itemWithCallNumber("prefix1", "cn1", "suffix1"),
      itemWithCallNumber("prefix2", "cn2", "suffix2"));

    assertThat(processor.getFieldValue(new Instance().items(items)))
      .containsExactlyInAnyOrder("prefix1 cn1 suffix1", "prefix2 cn2 suffix2");
  }

  @Test
  void canGetFieldValue_someComponentsAreNulls() {
    var items = List.of(
      itemWithCallNumber(null, "cn1", "suffix1"),
      itemWithCallNumber("prefix2", "cn2", null),
      itemWithCallNumber(null, "cn3", null));

    assertThat(processor.getFieldValue(new Instance().items(items)))
      .containsExactlyInAnyOrder("cn1 suffix1", "prefix2 cn2", "cn3");
  }

  @Test
  void shouldReturnEmptySetWhenNoItems() {
    assertThat(processor.getFieldValue(new Instance())).isEmpty();
  }

  @Test
  void shouldReturnEmptySetWhenItemsHasNoCallNumber() {
    var items = List.of(new Item(), new Item(),
      new Item().effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents()));

    assertThat(processor.getFieldValue(new Instance().items(items))).isEmpty();
  }

  private Item itemWithCallNumber(String prefix, String cn, String suffix) {
    return new Item().effectiveCallNumberComponents(
      new ItemEffectiveCallNumberComponents()
        .prefix(prefix).callNumber(cn).suffix(suffix));
  }
}
