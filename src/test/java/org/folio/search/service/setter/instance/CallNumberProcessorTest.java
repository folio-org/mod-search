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
import org.junit.jupiter.params.provider.CsvSource;
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

  @CsvSource({
    "A, AA", "A, AB", "AB, ABB", "A,AZ", "NZ,O", "EZ,F", "Z, ZZ", "1 1,1.2", "1,2", "EZZZZZZZZ,F", "EZ,G",
    "199999999,2", "0,1", "9,A", "9999999999, A", "99999999,a", "YZ,Z", "Z 12,Z12", "Z 12,Z.12",
    "HD 11, HD 22", "ZZ 8982, ZZ 9999999", "ABC, abc aab", "3327.21, 3327.21 OVERSIZE", "3325.21, 3325.22",
    "HD 11.2,HD 225.6", "HD 45214.8, HD 45214.9", "3100.12345, 3100.12346", "3100.12345, 3100/12346"
  })
  @DisplayName("getFieldValue_parameterized_comparePairs")
  @ParameterizedTest(name = "[{index}] cn1={0}, cn2={1}")
  void getFieldValue_parameterized(String firstCallNumber, String secondCallNumber) {
    var firstResult = callNumberProcessor.getCallNumberAsLong(firstCallNumber);
    var secondResult = callNumberProcessor.getCallNumberAsLong(secondCallNumber);
    assertThat(firstResult).isLessThan(secondResult);
  }

  private static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("item without effectiveShelvingOrder", instance(new Item()), emptyList()),
      arguments("item with null effectiveShelvingOrder", instance(item(null)), emptyList()),
      arguments("item with empty effectiveShelvingOrder", instance(item("")), emptyList()),
      arguments("item with blank effectiveShelvingOrder", instance(item(" ")), emptyList()),

      arguments("item shelf='abc'", instance(item("abc")), List.of(108827756302620654L)),
      arguments("item shelf='abc aab'", instance(item("abc aab")), List.of(108827803251592308L)),
      arguments("item shelf='abc aab j2'", instance(item("abc aab j2")), List.of(108827803251625965L)),
      arguments("item shelf='0'", instance(item("0")), List.of(24421218255574803L)),
      arguments("item shelf='0 '", instance(item("0 ")), List.of(24421218255574803L)),

      arguments("item shelf='3327.21'", instance(item("3327.21")), List.of(50122943013589875L)),
      arguments("item shelf='3327.21 OVERSIZE'", instance(item("3327.21 OVERSIZE")), List.of(50122943013632268L)),
      arguments("item shelf='3641.5943 M68l'", instance(item("3641.5943 M68L")), List.of(50759009019151524L)),
      arguments("item shelf='SHELF /1'", instance(item("SHELF /1")), List.of(256621496907955140L)),
      arguments("item shelf='SHELF '", instance(item("SHELF ")), List.of(256621496903090982L)),
      arguments("item shelf='SHELF'", instance(item("SHELF")), List.of(256621496903090982L)),

      // general class only
      arguments("item shelf='A'", instance(item("A")), List.of(105825279107490813L)),
      arguments("item shelf='Z '", instance(item("Z ")), List.of(309335431237280838L)),
      arguments("item shelf='ZZ'", instance(item("ZZ")), List.of(317267108961313680L)),

      // general class + simple classification number
      arguments("item shelf='HD 11'", instance(item("HD 11")), List.of(166148338481373924L)),
      arguments("item shelf='ZZ 999999999'", instance(item("ZZ 999999999")), List.of(317268799069501188L)),

      // general class + simple classification number + decimal value
      arguments("item shelf='HD 11.2'", instance(item("HD 11.2")), List.of(166148338583165328L)),
      arguments("item shelf='ZZ 99999999999.9'", instance(item("ZZ 9999999.9")), List.of(317268799069501188L)),

      // general class + classification + single cutter
      arguments("item shelf='A 11 I6'", instance(item("A 11 I6")), List.of(105847237984088601L)),
      arguments("item shelf='HD 44826 I6'", instance(item("HD 44826 I6")), List.of(166148761735193328L)),
      arguments("item shelf='HD 44826 I726'", instance(item("HD 44826 I726")), List.of(166148761735193328L)),
      arguments("item shelf='HD 44826 T9862'", instance(item("HD 44826 T9862")), List.of(166148761735193757L)),

      // general class + classification + double cutter
      arguments("item shelf='HD.4826 Y28172 I726'",
        instance(item("HD 44826 Y28172 I726")), List.of(166148761735193952L)),

      // general class + classification + cutter + publication year
      arguments("item shelf='N 47433.3 K94 42012'",
        instance(item("N 47433.3 K94 42012")), List.of(211689419776372395L)),

      // general class + classification + cutter + publication year
      arguments("item shelf='ZZ 9999999.9 Z9999 Z9999'",
        instance(item("ZZ 9999999.9 Z9999 Z9999")), List.of(317268799069501188L)),

      arguments("item shelves=['HD 11', 'HD 12', 'HD 11']",
        instance(item("HD 11"), item("HD 12"), item("HD 11")), List.of(166148338481373924L, 166148342000117685L))
    );
  }

  private static Instance instance(Item... items) {
    return new Instance().items(List.of(items));
  }

  private static Item item(String effectiveShelvingOrder) {
    return new Item().effectiveShelvingOrder(effectiveShelvingOrder);
  }
}
