package org.folio.search.service.setter.item;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class ItemIdentifiersProcessorTest {

  private static final List<String> FORMER_IDS = List.of(randomId(), randomId());
  private static final String UUID = randomId();
  private final ItemIdentifiersProcessor itemIdentifiersProcessor = new ItemIdentifiersProcessor();

  @MethodSource("testDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance eventBody, List<String> expected) {
    var actual = itemIdentifiersProcessor.getFieldValue(eventBody);
    assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
  }

  private static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("empty items", instance(), emptyList()),
      arguments("item with nullable identifier fields", instance(item(null, null, null)), emptyList()),
      arguments("item with hrid only", instance(item("i1", null, null)), List.of("i1")),
      arguments("item with UUID only", instance(item(UUID)), List.of(UUID)),
      arguments("item with accessionNumber only", instance(item(null, "an", null)), List.of("an")),
      arguments("item with empty identifiers", instance(item("", "", emptyList())), emptyList()),
      arguments("item with hrid and empty list in formerIds", instance(item("i1", null, emptyList())), List.of("i1")),
      arguments("item with single formerId", instance(item(null, null, List.of("id1"))), List.of("id1")),
      arguments("item with multiple formerIds", instance(item(null, null, FORMER_IDS)), FORMER_IDS),
      arguments("item with all identifiers and UUID", instance(
        item("i01", "an", List.of("fid")), item(UUID)), List.of("i01", "an", "fid", UUID)),
      arguments("2 duplicated items", instance(
        item("i01", "an", List.of("fid")), item("i01", "an", List.of("fid"))), List.of("i01", "an", "fid"))
    );
  }

  private static Instance instance(Item... items) {
    return new Instance().items(items != null ? Arrays.asList(items) : null);
  }

  private static Item item(String hrid, String accessionNumber, List<String> formerIds) {
    return new Item().hrid(hrid).accessionNumber(accessionNumber).formerIds(formerIds);
  }

  private static Item item(String id) {
    return new Item().id(id);
  }
}
