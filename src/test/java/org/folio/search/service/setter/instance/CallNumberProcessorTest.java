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

      arguments("item shelf='0'", instance(item("0")), List.of(32561624340766404L)),
      arguments("item shelf='0 '", instance(item("0 ")), List.of(32770352701925163L)),
      arguments("item shelf='1'", instance(item("1")), List.of(40702030425958005L)),
      arguments("item shelf='9'", instance(item("9")), List.of(105825279107490813L)),
      arguments("item shelf='abc'", instance(item("abc")), List.of(117182242758231495L)),
      arguments("item shelf='ABC'", instance(item("ABC")), List.of(117182242758231495L)),
      arguments("item shelf='abc aab'", instance(item("abc aab")), List.of(117182430549491229L)),
      arguments("item shelf='abc aab j2'", instance(item("abc aab j2")), List.of(117182430549585765L)),
      arguments("item shelf='0000000000'", instance(item("0000000000")), List.of(33418509191839200L)),
      arguments("item shelf='0000000000000'", instance(item("0000000000000")), List.of(33418509191839200L)),
      arguments("item shelf='YYYYYYYYYYYYY'", instance(item("YYYYYYYYYYYYY")), List.of(317475837322472400L)),
      arguments("item shelf='ZZZZZZZZZZ'", instance(item("ZZZZZZZZZZ")), List.of(325830464620432200L)),
      arguments("item shelf='zzzzzzzzzzzzz'", instance(item("zzzzzzzzzzzzz")), List.of(325830464620432200L)),
      arguments("item shelf='ZZZZZZZZZZZZZ'", instance(item("ZZZZZZZZZZZZZ")), List.of(325830464620432200L)),

      arguments("item shelf='3327.21'", instance(item("3327.21")), List.of(58477570311488796L)),
      arguments("item shelf='3327.21 OVERSIZE'", instance(item("3327.21 OVERSIZE")), List.of(58477570311592068L)),
      arguments("item shelf='3641.5943 M68l'", instance(item("3641.5943 M68L")), List.of(59113636317111324L)),
      arguments("item shelf='SHELF /1'", instance(item("SHELF /1")), List.of(264976124205913380L)),
      arguments("item shelf='SHELF '", instance(item("SHELF ")), List.of(264976124198676462L)),
      arguments("item shelf='SHELF'", instance(item("SHELF")), List.of(264976124108452263L)),

      // general class only
      arguments("item shelf='A'", instance(item("A")), List.of(113965685192682414L)),
      arguments("item shelf='B'", instance(item("B")), List.of(122106091277874015L)),
      arguments("item shelf='BA'", instance(item("BA")), List.of(125028288334096641L)),
      arguments("item shelf='BC'", instance(item("BC")), List.of(125445745056414159L)),
      arguments("item shelf='C'", instance(item("C")), List.of(130246497363065616L)),
      arguments("item shelf='SO'", instance(item("SO")), List.of(266337388838576484L)),
      arguments("item shelf='HD'", instance(item("HD")), List.of(174496909928722524L)),
      arguments("item shelf='N'", instance(item("N")), List.of(219790964300173227L)),
      arguments("item shelf='NN'", instance(item("NN")), List.of(225426630051459720L)),
      arguments("item shelf='NZ'", instance(item("NZ")), List.of(227931370385364828L)),
      arguments("item shelf='O'", instance(item("O")), List.of(227931370385364828L)),
      arguments("item shelf='Y'", instance(item("Y")), List.of(309335431237280838L)),
      arguments("item shelf='Z'", instance(item("Z")), List.of(317475837322472439L)),
      arguments("item shelf='ZZ'", instance(item("ZZ")), List.of(325616243407664040L)),

      // general class + simple classification number
      arguments("item shelf='HD 11'", instance(item("HD 11")), List.of(174502965686735205L)),
      arguments("item shelf='HD 15'", instance(item("HD 15")), List.of(174502979761710249L)),
      arguments("item shelf='HD 225'", instance(item("HD 225")), List.of(174503107248503436L)),
      arguments("item shelf='HD 294'", instance(item("HD 294")), List.of(174503131789485564L)),
      arguments("item shelf='HD 3561'", instance(item("HD 3561")), List.of(174503255137532802L)),
      arguments("item shelf='HD 44826'", instance(item("HD 44826")), List.of(174503389033150749L)),
      arguments("item shelf='HD 552141'", instance(item("HD 552141")), List.of(174503529239131521L)),
      arguments("item shelf='HD 6123456'", instance(item("HD 6123456")), List.of(174503652399796512L)),
      arguments("item shelf='ZZ 6999999'", instance(item("ZZ 6999999")), List.of(325623014674440951L)),
      arguments("item shelf='ZZ 9999999'", instance(item("ZZ 9999999")), List.of(325623426367460988L)),
      arguments("item shelf='ZZ 999999999'", instance(item("ZZ 999999999")), List.of(325623426367460988L)),

      // general class + simple classification number + decimal value
      arguments("item shelf='HD 11.2'", instance(item("HD 11.2")), List.of(174502965881064249L)),
      arguments("item shelf='HD 225.6'", instance(item("HD 225.6")), List.of(174503107253723508L)),
      arguments("item shelf='HD 3561.1'", instance(item("HD 3561.1")), List.of(174503255137659045L)),
      arguments("item shelf='HD 44826.8'", instance(item("HD 44826.8")), List.of(174503389033154259L)),
      arguments("item shelf='HD 44826.86'", instance(item("HD 44826.8")), List.of(174503389033154259L)),
      arguments("item shelf='HD 44826.81'", instance(item("HD 44826.8")), List.of(174503389033154259L)),
      arguments("item shelf='HD 552146.9'", instance(item("HD 552146.9")), List.of(174503529239139204L)),
      arguments("item shelf='HD 552141.92'", instance(item("HD 552141.92")), List.of(174503529239131599L)),
      arguments("item shelf='HD 552141.98'", instance(item("HD 552141.98")), List.of(174503529239131599L)),
      arguments("item shelf='HD 6123456.5'", instance(item("HD 6123456.5")), List.of(174503652399796512L)),
      arguments("item shelf='ZZ 6999999.9'", instance(item("ZZ 6999999.9")), List.of(325623014674440951L)),
      arguments("item shelf='ZZ 99999999999.9'", instance(item("ZZ 9999999.9")), List.of(325623426367460988L)),

      // general class + classification + single cutter
      arguments("item shelf='A 11 I6'", instance(item("A 11 I6")), List.of(114201865281987522L)),
      arguments("item shelf='HD 44826 I6'", instance(item("HD 44826 I6")), List.of(174503389033153128L)),
      arguments("item shelf='HD 44826 I726'", instance(item("HD 44826 I726")), List.of(174503389033153128L)),
      arguments("item shelf='HD 44826 T9862'", instance(item("HD 44826 T9862")), List.of(174503389033153557L)),

      // general class + classification + double cutter
      arguments("item shelf='HD.4826 Y28172 I726'",
        instance(item("HD 44826 Y28172 I726")), List.of(174503389033153752L)),

      // general class + classification + cutter + publication year
      arguments("item shelf='N 47433.3 K94 42012'",
        instance(item("N 47433.3 K94 42012")), List.of(220044047074332195L)),

      // general class + classification + cutter + publication year
      arguments("item shelf='ZZ 9999999.9 Z9999 Z9999'",
        instance(item("ZZ 9999999.9 Z9999 Z9999")), List.of(325623426367460988L)),

      arguments("item shelves=['HD 11', 'HD 12', 'HD 11']",
        instance(item("HD 11"), item("HD 12"), item("HD 11")), List.of(174502965686735205L, 174502969205478966L))
    );
  }

  private static Instance instance(Item... items) {
    return new Instance().items(List.of(items));
  }

  private static Item item(String effectiveShelvingOrder) {
    return new Item().effectiveShelvingOrder(effectiveShelvingOrder);
  }
}
