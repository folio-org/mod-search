package org.folio.search.service.setter.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ItemCallNumberProcessorTest {

  private final ItemCallNumberProcessor callNumberProcessor = new ItemCallNumberProcessor();

  @Test
  void getFieldValue_multipleValue_positive() {
    var eventBody = instance(item("HD 11"), item("HD 12"), item("HD 11"));
    var actual = callNumberProcessor.getFieldValue(eventBody);
    assertThat(actual).containsExactly(4408940162027048960L, 4408940181797658624L);
  }

  @Test
  void getFieldValue_emptyAfterNormalization() {
    var eventBody = instance(item("()[]"));
    var actual = callNumberProcessor.getFieldValue(eventBody);
    assertThat(actual).isEmpty();
  }

  private static Instance instance(Item... items) {
    return new Instance().items(List.of(items));
  }

  private static Item item(String effectiveShelvingOrder) {
    return new Item().effectiveShelvingOrder(effectiveShelvingOrder);
  }
}
