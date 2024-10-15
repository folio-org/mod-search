package org.folio.search.service.setter.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ItemEffectiveShelvingOrderProcessorTest {

  private final ItemEffectiveShelvingOrderProcessor
    itemEffectiveShelvingOrderProcessor = new ItemEffectiveShelvingOrderProcessor();

  @Test
  void getFieldValue_positive() {
    var instance = new Instance().items(List.of(
      new Item().effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().callNumber("A1")),
      new Item().effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().callNumber("F-132,23")),
      new Item().effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().callNumber("unknown")),
      new Item().effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().callNumber("[]](測試)"))));
    var actual = itemEffectiveShelvingOrderProcessor.getFieldValue(instance);
    assertThat(actual).containsExactly("A 11", "F-132,23", "UNKNOWN");
  }

}
