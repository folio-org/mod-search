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
import static org.opensearch.index.query.QueryBuilders.boolQuery;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import one.util.streamex.StreamEx;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.model.service.BrowseContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.index.query.TermQueryBuilder;

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
    var context = BrowseContext.builder().build();
    var items = CallNumberUtils.excludeIrrelevantResultItems(context, callNumberType, emptySet(), given);
    assertThat(items).isEqualTo(expected);
    var unchangedItems = CallNumberUtils.excludeIrrelevantResultItems(context, "", emptySet(), given);
    assertThat(unchangedItems).isEqualTo(given);
  }

  @Test
  public void excludeIrrelevantResultItems_with_null_iecnc() {
    var context = BrowseContext.builder().build();
    var givenItems = createItemWithNullEffectiveCallNumberComponents();
    var callNumberTypeValue = "dewey";

    var resultItems = CallNumberUtils
      .excludeIrrelevantResultItems(context, callNumberTypeValue, emptySet(), givenItems);

    assertThat(resultItems).isEmpty();
  }

  @Test
  void excludeIrrelevantResultItems_positive_tenantFilter() {
    var tenantId = "tenant";
    var context = BrowseContext.builder()
      .filters(List.of(new TermQueryBuilder("holdings.tenantId", tenantId)))
      .build();
    var data = List.<List<String>>of(newArrayList(null, "cn", "00000000-0000-0000-0000-000000000006"));
    var browseItems = List.of(browseItem(data, "id", "cn", TENANT_ID),
      browseItem(data, "id", "cn", tenantId));
    var expected = List.of(browseItems.get(1));

    var items = CallNumberUtils.excludeIrrelevantResultItems(context, null, emptySet(), browseItems);
    assertThat(items).isEqualTo(expected);
  }

  @Test
  void excludeIrrelevantResultItems_positive_locationFilter() {
    var effectiveLocationId = UUID.randomUUID().toString();
    var context = BrowseContext.builder()
      .filters(List.of(new TermQueryBuilder("items.effectiveLocationId", effectiveLocationId)))
      .build();
    var data = List.<List<String>>of(newArrayList(null, "cn", "00000000-0000-0000-0000-000000000006"));
    var browseItems = List.of(browseItem(data, "id", "cn", TENANT_ID),
      browseItem(data, "id", "cn", TENANT_ID, effectiveLocationId));
    var expected = List.of(browseItems.get(1));

    var items = CallNumberUtils.excludeIrrelevantResultItems(context, null, emptySet(), browseItems);
    assertThat(items).isEqualTo(expected);
  }

  @Test
  void excludeIrrelevantResultItems_positive_multipleLocationFilter() {
    var effectiveLocationId1 = UUID.randomUUID().toString();
    var effectiveLocationId2 = UUID.randomUUID().toString();
    var context = BrowseContext.builder()
      .filters(List.of(boolQuery()
        .should(new TermQueryBuilder("items.effectiveLocationId", effectiveLocationId1))
        .should(new TermQueryBuilder("items.effectiveLocationId", effectiveLocationId2))))
      .build();
    var data = List.<List<String>>of(newArrayList(null, "cn", "00000000-0000-0000-0000-000000000006"));
    var browseItems = List.of(browseItem(data, "id", "cn", TENANT_ID),
      browseItem(data, "id", "cn", TENANT_ID, effectiveLocationId1),
      browseItem(data, "id", "cn", TENANT_ID, effectiveLocationId2));
    var expected = browseItems.subList(1, 3);

    var items = CallNumberUtils.excludeIrrelevantResultItems(context, null, emptySet(), browseItems);
    assertThat(items).isEqualTo(expected);
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
    return browseItem(data, instanceId, fullCallNumber, TENANT_ID);
  }

  private static CallNumberBrowseItem browseItem(List<List<String>> data, String instanceId, String fullCallNumber,
                                                 String tenantId) {
    return browseItem(data, instanceId, fullCallNumber, tenantId, null);
  }

  private static CallNumberBrowseItem browseItem(List<List<String>> data, String instanceId, String fullCallNumber,
                                                 String tenantId, String effectiveLocationId) {
    var items = data.stream().map(d -> new Item()
        .id(d.get(2))
        .tenantId(tenantId)
        .effectiveLocationId(effectiveLocationId)
        .discoverySuppress(false)
        .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents()
          .callNumber(d.get(1))
          .suffix(d.size() > 3 ? d.get(3) : null)
          .typeId(d.get(0)))
        .effectiveShelvingOrder(getShelfKeyFromCallNumber(d.get(1))))
      .toList();

    var instance = new Instance()
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

  public static List<CallNumberBrowseItem> createItemWithNullEffectiveCallNumberComponents() {
    var testId = randomId();
    var data = List.of(newArrayList(DEWEY.getId(), "308 H977", "00000000-0000-0000-0000-000000000001"));

    var items = data.stream().map(d -> new Item()
        .id(d.get(2))
        .tenantId("tenant")
        .effectiveLocationId(testId)
        .discoverySuppress(false)
        .effectiveCallNumberComponents(null)
        .effectiveShelvingOrder(getShelfKeyFromCallNumber(d.get(1))))
      .toList();

    var instance = new Instance()
      .id(testId)
      .title("instance #01")
      .staffSuppress(false)
      .discoverySuppress(false)
      .isBoundWith(false)
      .shared(false)
      .tenantId(TENANT_ID)
      .items(items)
      .holdings(emptyList());

    var callNumberBrowseItem = new CallNumberBrowseItem()
      .fullCallNumber("cn")
      .instance(instance);

    return List.of(callNumberBrowseItem);
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
