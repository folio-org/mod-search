package org.folio.search.service.setter.holding;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class HoldingsTypeIdProcessorTest {
  private final HoldingsTypeIdProcessor holdingsTypeIdProcessor = new HoldingsTypeIdProcessor();

  @MethodSource("holdingsTypesDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance instance, List<String> expected) {
    var actual = holdingsTypeIdProcessor.getFieldValue(instance);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  private static Stream<Arguments> holdingsTypesDataProvider() {
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("empty holding", new Instance().addHoldingsItem(new Holding()), emptyList()),
      arguments("holdings with null typeId", instanceWithHoldingsTypeId((String) null), emptyList()),
      arguments("holdings with empty typeId", instanceWithHoldingsTypeId(""), emptyList()),
      arguments("holdings with 2 typeId", instanceWithHoldingsTypeId("id1", "id2"), List.of("id2", "id1")),
      arguments("2 holdings typeId", instanceWithHoldingsTypeId("id1", "id2", "id1", "id2"), List.of("id2", "id1")),
      arguments("holdings typeId(trailing spaces)", instanceWithHoldingsTypeId("id  "), List.of("id")),
      arguments("holdings typeId(leading spaces)", instanceWithHoldingsTypeId("  id"), List.of("id")),
      arguments("holdings typeId(leading and trailing)", instanceWithHoldingsTypeId("  id  "), List.of("id")),
      arguments("holdings typeId(empty)", instanceWithHoldingsTypeId(""), emptyList()),
      arguments("holdings typeId(blank)", instanceWithHoldingsTypeId(" "), emptyList())
    );
  }

  private static Instance instanceWithHoldingsTypeId(String... ids) {
    var instance = new Instance();
    Arrays.stream(ids).forEach(id -> instance.addHoldingsItem(new Holding().holdingsTypeId(id)));
    return instance;
  }
}
