package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class CallNumberProcessorTest {

  private final CallNumberProcessor callNumberProcessor = new CallNumberProcessor();

  @MethodSource("testDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance eventBody, List<Long> expected) {
    var actual = callNumberProcessor.getFieldValue(eventBody);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  private static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("item without effectiveShelvingOrder", instance(new Item()), emptyList()),
      arguments("item with null effectiveShelvingOrder", instance(item(null)), emptyList()),
      arguments("item with empty effectiveShelvingOrder", instance(item("")), emptyList()),
      arguments("item with blank effectiveShelvingOrder", instance(item(" ")), emptyList()),

      arguments("item shelf='abc'", instance(item("abc")), emptyList()),
      arguments("item shelf='abc aab'", instance(item("abc aab")), emptyList()),
      arguments("item shelf='abc aab j2'", instance(item("abc aab j2")), emptyList()),

      // general class only
      arguments("item shelf='A'", instance(item("A")), List.of(cnValue(1))),
      arguments("item shelf='B'", instance(item("B")), List.of(cnValue(28))),
      arguments("item shelf='BA'", instance(item("BA")), List.of(cnValue(29))),
      arguments("item shelf='BC'", instance(item("BC")), List.of(cnValue(31))),
      arguments("item shelf='C'", instance(item("C")), List.of(cnValue(55))),
      arguments("item shelf='SO'", instance(item("SO")), List.of(cnValue(502))),
      arguments("item shelf='HD'", instance(item("HD")), List.of(cnValue(194))),
      arguments("item shelf='N'", instance(item("N")), List.of(cnValue(352))),
      arguments("item shelf='NN'", instance(item("NN")), List.of(cnValue(366))),
      arguments("item shelf='Z'", instance(item("Z")), List.of(cnValue(676))),
      arguments("item shelf='ZZ'", instance(item("ZZ")), List.of(cnValue(702))),

      // general class + simple classification number
      arguments("item shelf='HD 11'", instance(item("HD 11")), List.of(cnValue(194, 110000))),
      arguments("item shelf='HD 15'", instance(item("HD 15")), List.of(cnValue(194, 150000))),
      arguments("item shelf='HD 225'", instance(item("HD 225")), List.of(cnValue(194, 225000))),
      arguments("item shelf='HD 294'", instance(item("HD 294")), List.of(cnValue(194, 294000))),
      arguments("item shelf='HD 3561'", instance(item("HD 3561")), List.of(cnValue(194, 356100))),
      arguments("item shelf='HD 44826'", instance(item("HD 44826")), List.of(cnValue(194, 448260))),
      arguments("item shelf='HD 552141'", instance(item("HD 552141")), List.of(cnValue(194, 552141))),
      arguments("item shelf='HD 6123456'", instance(item("HD 6123456")), List.of(cnValue(194, 612345))),
      arguments("item shelf='ZZ 6999999'", instance(item("ZZ 6999999")), List.of(cnValue(702, 699999))),
      arguments("item shelf='ZZ 999999999'", instance(item("ZZ 999999999")), List.of(cnValue(702, 999999))),

      // general class + simple classification number + decimal value
      arguments("item shelf='HD 11.2'", instance(item("HD 11.2")), List.of(cnValue(194, 110002))),
      arguments("item shelf='HD 225.6'", instance(item("HD 225.6")), List.of(cnValue(194, 225006))),
      arguments("item shelf='HD 3561.1'", instance(item("HD 3561.1")), List.of(cnValue(194, 356101))),
      arguments("item shelf='HD 44826.8'", instance(item("HD 44826.8")), List.of(cnValue(194, 448268))),
      arguments("item shelf='HD 44826.86'", instance(item("HD 44826.8")), List.of(cnValue(194, 448268))),
      arguments("item shelf='HD 44826.81'", instance(item("HD 44826.8")), List.of(cnValue(194, 448268))),
      arguments("item shelf='HD 552146.9'", instance(item("HD 552146.9")), List.of(cnValue(194, 552146))),
      arguments("item shelf='HD 552141.92'", instance(item("HD 552141.92")), List.of(cnValue(194, 552141))),
      arguments("item shelf='HD 552141.98'", instance(item("HD 552141.98")), List.of(cnValue(194, 552141))),
      arguments("item shelf='HD 6123456.5'", instance(item("HD 6123456.5")), List.of(cnValue(194, 612345))),
      arguments("item shelf='ZZ 6999999.9'", instance(item("ZZ 6999999.9")), List.of(cnValue(702, 699999))),
      arguments("item shelf='ZZ 99999999999.9'", instance(item("ZZ 9999999.9")), List.of(cnValue(702, 999999))),

      // general class + classification + single cutter
      arguments("item shelf='A 11 I6'", instance(item("A 11 I6")), List.of(cnValue(1, 110000, 9600))),
      arguments("item shelf='HD 44826 I6'", instance(item("HD 44826 I6")), List.of(cnValue(194, 448260, 9600))),
      arguments("item shelf='HD 44826 I726'", instance(item("HD 44826 I726")), List.of(cnValue(194, 448260, 9726))),
      arguments("item shelf='HD 44826 T9862'", instance(item("HD 44826 T9862")), List.of(cnValue(194, 448260, 20986))),

      // general class + classification + double cutter
      arguments("item shelf='HD.4826 Y28172 I726'",
        instance(item("HD 44826 Y28172 I726")), List.of(cnValue(194, 448260, 25281, 9726))),

      // general class + classification + cutter + publication year
      arguments("item shelf='N 47433.3 K94 42012'",
        instance(item("N 47433.3 K94 42012")), List.of(cnValue(352, 474333, 11940))),

      // general class + classification + cutter + publication year
      arguments("item shelf='ZZ 9999999.9 Z9999 Z9999'",
        instance(item("ZZ 9999999.9 Z9999 Z9999")), List.of(cnValue(702, 999999, 26999, 26999))),

      arguments("item shelves=['HD 11', 'HD 12', 'HD 11']",
        instance(item("HD 11"), item("HD 12"), item("HD 11")), List.of(cnValue(194, 110000), cnValue(194, 120000)))
    );
  }

  private static Instance instance(Item... items) {
    return new Instance().items(List.of(items));
  }

  private static Item item(String effectiveShelvingOrder) {
    return new Item().effectiveShelvingOrder(effectiveShelvingOrder);
  }

  private static long cnValue(long... numbers) {
    var general = getValueOrDefault(numbers, 0);
    var classification = getValueOrDefault(numbers, 1);
    var firstCutter = getValueOrDefault(numbers, 2);
    var secondCutter = getValueOrDefault(numbers, 3);
    return general * (long) 1e16 + classification * (long) 1e10 + firstCutter * (long) 1e5 + secondCutter;
  }

  private static long getValueOrDefault(long[] values, int index) {
    return values.length > index ? values[index] : 0L;
  }
}
