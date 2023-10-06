package org.folio.search.utils;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.apache.commons.lang3.StringUtils.compareIgnoreCase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.CallNumberType.DEWEY;
import static org.folio.search.model.types.CallNumberType.LC;
import static org.folio.search.model.types.CallNumberType.NLM;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.getShelfKeyFromCallNumber;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import one.util.streamex.StreamEx;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class CallNumberUtilsTest {

  @DisplayName("getFieldValue_parameterized_comparePairs")
  @ParameterizedTest(name = "[{index}] cn1={0}, cn2={1}")
  @CsvFileSource(resources = {
    "/samples/cn-browse/cn-browse-common.csv",
    "/samples/cn-browse/cn-browse-lc-numbers.csv",
    "/samples/cn-browse/cn-browse-dewey-numbers.csv",
    "/samples/cn-browse/cn-browse-other-schema.csv"
  })
  void getFieldValue_comparedPairs_parameterized(String firstCallNumber, String secondCallNumber) {
    assertThat(compareIgnoreCase(firstCallNumber, secondCallNumber)).isNegative();

    var firstResult = CallNumberUtils.getCallNumberAsLong(firstCallNumber);
    var secondResult = CallNumberUtils.getCallNumberAsLong(secondCallNumber);

    assertThat(firstResult).isLessThan(secondResult).isNotNegative();
    assertThat(secondResult).isNotNegative();
  }

  @CsvSource({
    "aaa,AAA",
    "abc,ABC",
    "ab\\as,AB\\AS",
    "ab№as,AB AS"
  })
  @ParameterizedTest
  void normalizeEffectiveShelvingOrder_positive(String given, String expected) {
    var actual = CallNumberUtils.normalizeEffectiveShelvingOrder(given);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void normalizeEffectiveShelvingOrder_positive_forNull() {
    var actual = CallNumberUtils.normalizeEffectiveShelvingOrder(null);
    assertThat(actual).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("supportedCharactersDataset")
  void isSupportedCharacter_positive(char given) {
    var actual = CallNumberUtils.isSupportedCharacter(given);
    assertThat(actual).isTrue();
  }

  @ParameterizedTest
  @MethodSource("letterCharacterDataProvider")
  void getIntValue_positive_letters(char given) {
    var actual = CallNumberUtils.getIntValue(given, 0);
    assertThat(actual).isEqualTo(given - 42);
  }

  @ParameterizedTest
  @MethodSource("numericCharacterDataProvider")
  void getIntValue_positive_numbers(char given) {
    var actual = CallNumberUtils.getIntValue(given, 0);
    assertThat(actual).isEqualTo(given - 40);
  }

  @CsvSource({
    "' ',0", "$,0", "!,1", "#,2", "+,3", "',',4", "-,5", ".,6", "/,7", ":,18", ";,19",
    "=,20", "?,21", "@,22", "\\,49", "_,50", "~,51"
  })
  @ParameterizedTest
  void getIntValue_positive_otherCharacters(char given, int expected) {
    var actual = CallNumberUtils.getIntValue(given, 0);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void getEffectiveCallNumber_positive() {
    var actual = CallNumberUtils.getEffectiveCallNumber("prefix", "cn", null);
    assertThat(actual).isEqualTo("prefix cn");
  }

  @Test
  void getNormalizedCallNumber_positive() {
    var actual = CallNumberUtils.normalizeCallNumberComponents(null, "94 NF 14/1:3792-3835", null);
    assertThat(actual).isEqualTo("94nf14137923835");
  }

  @Test
  void getNormalizedCallNumber_with_suffix_prefix_positive() {
    var actual = CallNumberUtils.normalizeCallNumberComponents("prefix", "94 NF 14/1:3792-3835", "suffix");
    assertThat(actual).isEqualTo("prefix94nf14137923835suffix");
  }

  @ParameterizedTest(name = "[{index}] callNumber={0}, records={1}, expected={2}")
  @MethodSource("eliminateIrrelevantItemsOnCallNumberBrowsingData")
  void excludeIrrelevantResultItems(String callNumberType, List<CallNumberBrowseItem> given,
                                    List<CallNumberBrowseItem> expected) {
    var items = CallNumberUtils.excludeIrrelevantResultItems(callNumberType, emptySet(), given);
    assertThat(items).isEqualTo(expected);
    var unchangedItems = CallNumberUtils.excludeIrrelevantResultItems("", emptySet(), given);
    assertThat(unchangedItems).isEqualTo(given);
  }

  private static Stream<Arguments> supportedCharactersDataset() {
    return StreamEx.<Arguments>empty()
      .append(letterCharacterDataProvider())
      .append(numericCharacterDataProvider())
      .append(otherCharactersDataProvider());
  }

  private static Stream<Arguments> letterCharacterDataProvider() {
    return IntStream.rangeClosed('A', 'Z').mapToObj(e -> arguments((char) e));
  }

  private static Stream<Arguments> numericCharacterDataProvider() {
    return IntStream.rangeClosed('0', '9').mapToObj(e -> arguments((char) e));
  }

  private static Stream<Arguments> otherCharactersDataProvider() {
    return ".,:;=-+~_/\\#@?!".chars().mapToObj(e -> arguments((char) e));
  }

  private static CallNumberBrowseItem browseItem(List<List<String>> data, String instanceId, String fullCallNumber) {
    var items = data.stream().map(d -> new Item()
        .id(d.get(2))
        .tenantId(TENANT_ID)
        .discoverySuppress(false)
        .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents()
          .callNumber(d.get(1))
          .suffix(d.size() > 3 ? d.get(3) : null)
          .typeId(d.get(0)))
        .effectiveShelvingOrder(getShelfKeyFromCallNumber(d.get(1))))
      .toList();

    var instance =  new Instance()
      .id(instanceId)
      .title("instance #01")
      .staffSuppress(false)
      .discoverySuppress(false)
      .isBoundWith(false)
      .shared(false)
      .tenantId(TENANT_ID)
      .items(items)
      .holdings(emptyList());
    return new CallNumberBrowseItem()
      .fullCallNumber(fullCallNumber)
      .instance(instance);
  }

  private static Stream<Arguments> eliminateIrrelevantItemsOnCallNumberBrowsingData() {
    var id = randomId();
    var mixedData = List.of(
      List.of(LC.getId(), "Z669.R360 197", "00000000-0000-0000-0000-000000000000"),
      List.of(DEWEY.getId(), "308 H977", "00000000-0000-0000-0000-000000000001")
    );
    var deweyData = List.of(
      List.of(DEWEY.getId(), "308 H977", "00000000-0000-0000-0000-000000000001")
    );

    var mixedLcData = List.of(
      List.of(LC.getId(), "Z669.R360 197", "00000000-0000-0000-0000-000000000000"),
      List.of(DEWEY.getId(), "308 H977", "00000000-0000-0000-0000-000000000001"),
      List.of(NLM.getId(), "WE 200-600", "00000000-0000-0000-0000-000000000002"),
      List.of(NLM.getId(), "WE 200-700", "00000000-0000-0000-0000-000000000003")
    );

    var lcData = List.of(
      List.of(NLM.getId(), "WE 200-600", "00000000-0000-0000-0000-000000000002"),
      List.of(NLM.getId(), "WE 200-700", "00000000-0000-0000-0000-000000000003")
    );

    var nlmSuffixData = List.of(
      List.of(NLM.getId(), "QS 11 .GA1 E53", "00000000-0000-0000-0000-000000000004", "2005")
    );

    var localData = List.of(
      List.of(UUID.randomUUID().toString(), "localCn1", "00000000-0000-0000-0000-000000000004")
    );
    var localAndNotTypedData = List.of(
      localData.get(0),
      newArrayList(null, "noTypedCn", "00000000-0000-0000-0000-000000000006")
    );


    return Stream.of(
      arguments("dewey", List.of(browseItem(mixedData, id, "308 H977")),
        List.of(browseItem(deweyData, id, "308 H977"))),
      arguments("nlm", List.of(browseItem(mixedLcData, id, "WE 200-600")),
        List.of(browseItem(lcData, id, "WE 200-600"))),
      arguments("nlm", List.of(browseItem(nlmSuffixData, id, "QS 11 .GA1 E53 2005")),
        List.of(browseItem(nlmSuffixData, id, "QS 11 .GA1 E53 2005"))),
      arguments("local", List.of(browseItem(localAndNotTypedData, id, "localCn1")),
        List.of(browseItem(localData, id, "localCn1")))
    );
  }

}
