package org.folio.search.service.setter.holding;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class HoldingIdentifiersProcessorTest {

  private static final List<String> FORMER_IDS = List.of(randomId(), randomId());
  private static final String UUID = randomId();
  private final HoldingIdentifiersProcessor itemIdentifiersProcessor = new HoldingIdentifiersProcessor();

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
      arguments("holding with nullable identifier fields", instance(holding(null, null)), emptyList()),
      arguments("holding with hrid only", instance(holding("h01", null)), List.of("h01")),
      arguments("holding with UUID only", instance(holding(UUID)), List.of(UUID)),
      arguments("holding with empty identifiers", instance(holding("", emptyList())), emptyList()),
      arguments("holding with hrid and empty list in formerIds", instance(holding("h01", emptyList())), List.of("h01")),
      arguments("holding with single formerId", instance(holding(null, List.of("id1"))), List.of("id1")),
      arguments("holding with multiple formerIds", instance(holding(null, FORMER_IDS)), FORMER_IDS),
      arguments("holding with all identifiers", instance(holding("h01", List.of("fid"))), List.of("h01", "fid")),
      arguments("holding with all identifiers and UUID", instance(
        holding("h01", List.of("fid")), holding(UUID)), List.of("h01", "fid", UUID)),
      arguments("2 duplicated holdings", instance(
        holding("h01", List.of("fid")), holding("h01", List.of("fid"))), List.of("h01", "fid"))
    );
  }

  private static Instance instance(Holding... holdings) {
    return new Instance().holdings(holdings != null ? Arrays.asList(holdings) : null);
  }

  private static Holding holding(String hrid, List<String> formerIds) {
    return new Holding().hrid(hrid).formerIds(formerIds);
  }

  private static Holding holding(String id) {
    return new Holding().id(id);
  }
}
