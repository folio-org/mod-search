package org.folio.search.service.setter.instance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.folio.search.service.indexrebuild.callnumber.CallNumberKey;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnitTest
class CallNumberIdsProcessorTest {

  private final CallNumberIdsProcessor processor = new CallNumberIdsProcessor();

  @Test
  @DisplayName("Returns empty set when instance has no items field")
  void getFieldValue_positive_noItemsField() {
    assertThat(processor.getFieldValue(Map.of())).isEmpty();
  }

  @Test
  @DisplayName("Returns empty set when items value is null")
  void getFieldValue_positive_nullItems() {
    var instance = new HashMap<String, Object>();
    instance.put("items", null);

    assertThat(processor.getFieldValue(instance)).isEmpty();
  }

  @Test
  @DisplayName("Returns empty set when items list is empty")
  void getFieldValue_positive_emptyItems() {
    assertThat(processor.getFieldValue(Map.of("items", List.of()))).isEmpty();
  }

  @Test
  @DisplayName("Returns empty set when items have no components or a blank call number")
  void getFieldValue_positive_itemsWithoutCallNumber() {
    var instance = Map.<String, Object>of("items", List.of(
      Map.of("id", "item1"),
      Map.of("effectiveCallNumberComponents", Map.of("prefix", "pre")),
      Map.of("effectiveCallNumberComponents", Map.of("callNumber", "  "))));

    assertThat(processor.getFieldValue(instance)).isEmpty();
  }

  @Test
  @DisplayName("Returns call number id built from all effective call number components")
  void getFieldValue_positive_itemWithCallNumber() {
    var components = Map.of("callNumber", "QA76", "prefix", "pre", "suffix", "suf", "typeId", "type1");
    var instance = Map.<String, Object>of("items", List.of(Map.of("effectiveCallNumberComponents", components)));

    var expectedId = CallNumberKey.of("QA76", "pre", "suf", "type1").orElseThrow().id();

    assertThat(processor.getFieldValue(instance)).containsExactly(expectedId);
  }

  @Test
  @DisplayName("Collapses identical call numbers into one id and skips items without call number")
  void getFieldValue_positive_duplicatesAreCollapsedAndEmptyItemsSkipped() {
    var first = Map.of("effectiveCallNumberComponents", Map.of("callNumber", "QA76"));
    var duplicate = Map.of("id", "other", "effectiveCallNumberComponents", Map.of("callNumber", "QA76"));
    var second = Map.of("effectiveCallNumberComponents", Map.of("callNumber", "PS3545"));
    var withoutCallNumber = Map.of("id", "item4");
    var instance = Map.<String, Object>of("items", List.of(first, duplicate, second, withoutCallNumber));

    var expected = List.of(
      CallNumberKey.of("QA76", null, null, null).orElseThrow().id(),
      CallNumberKey.of("PS3545", null, null, null).orElseThrow().id());

    assertThat(processor.getFieldValue(instance)).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  @DisplayName("Returns distinct ids for the same call number with different prefix")
  void getFieldValue_positive_differentComponentsGiveDifferentIds() {
    var plain = Map.of("effectiveCallNumberComponents", Map.of("callNumber", "QA76"));
    var withPrefix = Map.of("effectiveCallNumberComponents", Map.of("callNumber", "QA76", "prefix", "pre"));
    var instance = Map.<String, Object>of("items", List.of(plain, withPrefix));

    assertThat(processor.getFieldValue(instance)).hasSize(2);
  }
}
