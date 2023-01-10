package org.folio.search.service.setter.holding;

import static java.util.Collections.emptyList;
import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.service.setter.instance.StatisticalCodesProcessor;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class StatisticalCodesProcessorTest {
  private final StatisticalCodesProcessor processor = new StatisticalCodesProcessor();

  @MethodSource("statisticalCodesProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance instance, List<String> expected) {
    var actual = processor.getFieldValue(instance);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  private static Stream<Arguments> statisticalCodesProvider() {
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("instance with statistical code", instanceWithStatisticalCodes(of("code1", "code2"), null, null),
        of("code2", "code1")),
      arguments("instance, holding with statistical code",
        instanceWithStatisticalCodes(of("code1"), of("code1", "code2"), null),
        of("code2", "code1")),
      arguments("instance, holding item with statistical code",
        instanceWithStatisticalCodes(of("code1"), of("code1", "code2"), of("code3", "code2")),
        of("code3", "code2", "code1"))
    );
  }

  private static Instance instanceWithStatisticalCodes(List<String> instanceCodes, List<String> holdingCodes,
                                                       List<String> itemCodes) {
    var instance = new Instance();
    instance.statisticalCodeIds(instanceCodes);
    if (holdingCodes != null) {
      instance.holdings(of(new Holding().statisticalCodeIds(holdingCodes)));
    }
    if (itemCodes != null) {
      instance.items(of(new Item().statisticalCodeIds(itemCodes)));
    }
    return instance;
  }
}
