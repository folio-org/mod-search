package org.folio.search.service.setter.item;

import static org.apache.commons.lang3.StringUtils.compareIgnoreCase;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

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

  @DisplayName("getFieldValue_parameterized_comparePairs")
  @ParameterizedTest(name = "[{index}] cn1={0}, cn2={1}")
  @CsvFileSource(resources = {
    "/samples/cn-browse/cn-browse-common.csv",
    "/samples/cn-browse/cn-browse-lc-numbers.csv",
    "/samples/cn-browse/cn-browse-dewey-numbers.csv",
    "/samples/cn-browse/cn-browse-other-schema.csv"
  })
  void getFieldValue_comparedPairs_parameterized(String firstCallNumber, String secondCallNumber) {
    assertThat(compareIgnoreCase(firstCallNumber, secondCallNumber)).isLessThan(0);

    var firstResult = callNumberProcessor.getCallNumberAsLong(firstCallNumber);
    var secondResult = callNumberProcessor.getCallNumberAsLong(secondCallNumber);

    assertThat(firstResult).isLessThan(secondResult);
    assertThat(firstResult).isGreaterThanOrEqualTo(0L);
    assertThat(secondResult).isGreaterThanOrEqualTo(0L);
  }

  private static Instance instance(Item... items) {
    return new Instance().items(List.of(items));
  }

  private static Item item(String effectiveShelvingOrder) {
    return new Item().effectiveShelvingOrder(effectiveShelvingOrder);
  }
}
