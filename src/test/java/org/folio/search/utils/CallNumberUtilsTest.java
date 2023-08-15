package org.folio.search.utils;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.compareIgnoreCase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.getShelfKeyFromCallNumber;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import one.util.streamex.StreamEx;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.Holding;
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
    "' ',0", "#,1", "$,2", "+,3", "',',4", "-,5", ".,6", "/,7", ":,18", ";,19",
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
  void excludeIrrelevantResultItems(String callNumberType, CallNumberBrowseItem given, CallNumberBrowseItem expected) {
    var items = CallNumberUtils.excludeIrrelevantResultItems(callNumberType, List.of(given));
    assertThat(items).isEqualTo(List.of(expected));
    var unchangedItems = CallNumberUtils.excludeIrrelevantResultItems("", List.of(given));
    assertThat(unchangedItems).isEqualTo(List.of(given));
  }

  @ParameterizedTest(name = "[{index}] callNumber={0}, records={1}, expected={2}")
  @MethodSource("eliminateIrrelevantItemsOnCallNumberBrowsingDataHoldings")
  void excludeIrrelevantResultHoldings(String callNumberType, CallNumberBrowseItem given,
                                       CallNumberBrowseItem expected) {
    var items = CallNumberUtils.excludeIrrelevantResultItems(callNumberType, List.of(given));
    assertThat(items).isEqualTo(List.of(expected));
    var unchangedItems = CallNumberUtils.excludeIrrelevantResultItems("", List.of(given));
    assertThat(unchangedItems).isEqualTo(List.of(given));
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
    return ".,:;=-+~_/\\#$@?".chars().mapToObj(e -> arguments((char) e));
  }

  private static CallNumberBrowseItem browseItem(List<List<String>> data, String instanceId) {
    var items = data.stream().map(d -> new Item()
        .id(d.get(2))
        .tenantId(TENANT_ID)
        .discoverySuppress(false)
        .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents()
          .callNumber(d.get(1))
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
      .instance(instance);
  }

  private static CallNumberBrowseItem browseItemHoldings(List<List<String>> data, String instanceId) {
    var holdings = data.stream().map(d -> new Holding()
        .id(d.get(2))
        .tenantId(TENANT_ID)
        .callNumber(d.get(1))
        .discoverySuppress(false))
      .toList();

    var instance =  new Instance()
      .id(instanceId)
      .title("instance #02")
      .staffSuppress(false)
      .discoverySuppress(false)
      .isBoundWith(false)
      .shared(false)
      .tenantId(TENANT_ID)
      .items(emptyList())
      .holdings(holdings);
    return new CallNumberBrowseItem()
      .instance(instance);
  }

  private static Stream<Arguments> eliminateIrrelevantItemsOnCallNumberBrowsingData() {
    var id = randomId();
    var mixedData = List.of(
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 197", "00000000-0000-0000-0000-000000000000"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H977", "00000000-0000-0000-0000-000000000001")
    );
    var deweyData = List.of(
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H977", "00000000-0000-0000-0000-000000000001")
    );

    var mixedLcData = List.of(
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 197", "00000000-0000-0000-0000-000000000000"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H977", "00000000-0000-0000-0000-000000000001"),
      List.of("054d460d-d6b9-4469-9e37-7a78a2266655", "WE 200-600", "00000000-0000-0000-0000-000000000002"),
      List.of("054d460d-d6b9-4469-9e37-7a78a2266655", "WE 200-700", "00000000-0000-0000-0000-000000000003")
    );

    var lcData = List.of(
      List.of("054d460d-d6b9-4469-9e37-7a78a2266655", "WE 200-600", "00000000-0000-0000-0000-000000000002"),
      List.of("054d460d-d6b9-4469-9e37-7a78a2266655", "WE 200-700", "00000000-0000-0000-0000-000000000003")
    );


    return Stream.of(
      arguments("dewey", browseItem(mixedData, id), browseItem(deweyData, id)),
      arguments("nlm", browseItem(mixedLcData, id), browseItem(lcData, id))
    );
  }

  private static Stream<Arguments> eliminateIrrelevantItemsOnCallNumberBrowsingDataHoldings() {
    var id = randomId();
    var mixedData = List.of(
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 197", "00000000-0000-0000-0000-000000000000"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H977", "00000000-0000-0000-0000-000000000001")
    );
    var deweyData = List.of(
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H977", "00000000-0000-0000-0000-000000000001")
    );

    var mixedLcData = List.of(
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 1970", "00000000-0000-0000-0000-000000000000"),
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 1980", "00000000-0000-0000-0000-000000000001"),
      List.of("054d460d-d6b9-4469-9e37-7a78a2266655", "WE 200-600", "00000000-0000-0000-0000-000000000002"),
      List.of("054d460d-d6b9-4469-9e37-7a78a2266655", "WE 200-700", "00000000-0000-0000-0000-000000000003")
    );

    var lcData = List.of(
      List.of("054d460d-d6b9-4469-9e37-7a78a2266655", "WE 200-600", "00000000-0000-0000-0000-000000000002"),
      List.of("054d460d-d6b9-4469-9e37-7a78a2266655", "WE 200-700", "00000000-0000-0000-0000-000000000003")
    );


    return Stream.of(
      arguments("dewey", browseItemHoldings(mixedData, id), browseItemHoldings(deweyData, id)),
      arguments("nlm", browseItemHoldings(mixedLcData, id), browseItemHoldings(lcData, id))
    );
  }

}
