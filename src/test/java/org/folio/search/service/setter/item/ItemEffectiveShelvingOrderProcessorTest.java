package org.folio.search.service.setter.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ItemEffectiveShelvingOrderProcessorTest {

  private final ItemEffectiveShelvingOrderProcessor
    itemEffectiveShelvingOrderProcessor = new ItemEffectiveShelvingOrderProcessor();

  @Test
  void getFieldValue_positive() {
    var instance = new Instance().items(List.of(
      new Item().effectiveShelvingOrder("A1"),
      new Item().effectiveShelvingOrder("F-132,23"),
      new Item().effectiveShelvingOrder("unknown"),
      new Item().effectiveShelvingOrder("[]](測試)")));
    var actual = itemEffectiveShelvingOrderProcessor.getFieldValue(instance);
    assertThat(actual).containsExactly("A1", "F-132,23", "UNKNOWN");
  }

}
