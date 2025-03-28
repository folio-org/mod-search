package org.folio.api.search;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Classification;
import org.folio.search.domain.dto.Dates;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemStatus;
import org.folio.search.domain.dto.Metadata;
import org.folio.search.domain.dto.RecordType;
import org.folio.search.domain.dto.Tags;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@IntegrationTest
class SearchInstanceFilterIT extends BaseIntegrationTest {

  private static final String AVAILABLE = "Available";
  private static final String CHECKED_OUT = "Checked out";
  private static final String MISSING = "Missing";

  private static final String[] IDS = array(
    "1353873c-0e5e-4d64-a2f9-6c444dc4cd46",
    "cc6bbc19-3f54-43c5-8736-b85688619641",
    "39a52d91-8dbb-4348-ab06-5c6115e600cd",
    "62f72eeb-ed5a-4619-b01f-1750d5528d25",
    "6d9ccc82-8142-4fbc-b6ba-8e3429fd9aca");

  private static final String[] ITEM_IDS = array(
    "fd37e8d3-adf3-44f8-8b10-658ad8ba8319",
    "712d0875-e57f-43f3-83be-41020ed51ecb",
    "fae9b380-d48e-4fb8-8a6a-f810449973af",
    "8b3afe85-0887-4f53-9104-1302e881bf25",
    "b2895b5d-c1eb-47e8-9402-90119104d765",
    "d44bc80e-ed6e-4c34-a24b-2cdbbc48b083",
    "ec4833b1-69c2-465d-8bc2-dcf4cd527d8c",
    "ec4833b1-69c2-465d-8bc2-dcf4cd527d8d"
  );

  private static final String[] HOLDINGS_IDS = array(
    "c9ee975f-ea3e-461d-b190-d1f3c11a35b0",
    "fe11e2b9-3751-48d4-8c2f-faa83f903931",
    "0cd836aa-4415-40a4-b6fe-35b5a1454669",
    "2b134022-85f0-4ebf-bc57-58b76b6b8e7a",
    "91274edf-0a9d-4cf7-82f3-9ee0c36e83e2",
    "d231a4ac-eac1-4eac-bc6d-baceb64a4984",
    "8ba03578-5c1f-4dcd-a49f-107882124f99"
  );

  private static final String[] FORMATS = array(
    "89f6d4f0-9cd2-4015-828d-331dc3adb47a",
    "25a81102-a2a9-4576-85ff-133ebcbcef2c",
    "e57e36a3-80ff-46a6-ac2f-5c8bd79bc2bb");

  private static final String[] TYPES = array(
    "24da24dd-03ae-4e34-bad6-c79e342baeb9",
    "de9e38bb-89a8-43ee-922f-c973b122cbb3");

  private static final String[] LOCATIONS = array(
    "ce23dfa1-17e8-4a1f-ad6b-34ce6ab352c2",
    "f1a49577-5096-4771-a8a0-d07d642241eb");

  private static final String[] PERMANENT_LOCATIONS = array(
    "765b4c3b-485c-4ce4-a117-f99c01ac49fe",
    "4fdca025-1629-4688-aeb7-9c5fe5c73549",
    "81f1ab2c-83c5-4a90-a8b7-c8c8179c0697");

  private static final String[] MATERIAL_TYPES = array(
    "c898029e-9a02-4b61-bedb-6956cff21bc2",
    "3d413322-1dee-431b-bd73-b1e399063260");

  private static final String[] STATUSES = array(
    "54cb0be0-2b5b-4da5-a687-32dec54b016a",
    "1117f093-0bfd-4324-aa3f-96c77f43b2bf"
  );

  private static final String[] HOLDINGS_TYPES = array(
    "eb003b9d-86f2-4bdf-9f8e-28851122617d",
    "d02bb1e2-fa7f-4354-a9f4-1ca9b81510a2");

  private static final String[] CLASSIFICATION_TYPE_IDS = array(
    "5af5cb9d-063f-48ea-8148-7da3ecaafd7d",
    "7e5684a9-c8c1-4c1e-85b9-d047f53eeb6d");

  private static final String[] DATES = array(
    "2021", "ddd9", "2020", "d99\\", "2023", "2022", "0", "1000");

  @BeforeAll
  static void prepare() {
    var holdingsMatcher = jsonPath("sum($.instances..holdings.length())", is((double) HOLDINGS_IDS.length));
    var itemsMatcher = jsonPath("sum($.instances..items.length())", is((double) ITEM_IDS.length));
    setUpTenant(List.of(holdingsMatcher, itemsMatcher), instances());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @MethodSource("filteredSearchQueriesProvider")
  @DisplayName("searchByInstances_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}")
  void searchByInstances_parameterized(String query, List<String> expectedIds) throws Exception {
    doSearchByInstances(query)
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(expectedIds.size())))
      .andExpect(jsonPath("instances[*].id", is(expectedIds)));
  }

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForInstances_parameterized")
  void getFacetsForInstances_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(doGet(recordFacetsPath(RecordType.INSTANCES, query, facets)), FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).isNotNull();
      assertThat(actualFacet.getValues())
        .containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }

  @MethodSource("invalidDateSearchQueriesProvider")
  @DisplayName("searchByInvalidDates_parameterized")
  @ParameterizedTest(name = "[{index}] value={1}")
  void searchByInstances_negative_invalidDateFormat(String name, String value) throws Exception {
    attemptSearchByInstances("(" + name + "==" + value + ")")
      .andExpect(status().isUnprocessableEntity())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Invalid date format")))
      .andExpect(jsonPath("$.errors[0].type", is("ValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is(name)))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(value)));
  }

  @Test
  void searchByInstances_negative_invalidFacetName() throws Exception {
    attemptGet(recordFacetsPath(RecordType.INSTANCES, "cql.allRecords=1", "unknownFacet:5"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Invalid facet value")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("facet")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("unknownFacet")));
  }

  private static Stream<Arguments> filteredSearchQueriesProvider() {
    return Stream.of(
      arguments("(id=*) sortby title", List.of(IDS)),
      arguments("(id=* and source==\"FOLIO\") sortby title", List.of(IDS[2], IDS[4])),

      arguments("(id=* and languages==\"eng\") sortby title", List.of(IDS[0], IDS[1], IDS[4])),
      arguments("(id=* and languages==\"ger\") sortby title", List.of(IDS[1])),
      arguments("(id=* and languages==\"ita\") sortby title", List.of(IDS[0], IDS[3])),
      arguments("(id=* and languages==\"fra\") sortby title", List.of(IDS[1], IDS[4])),
      arguments("(id=* and languages==\"rus\") sortby title", List.of(IDS[2])),
      arguments("(id=* and languages==\"ukr\") sortby title", List.of(IDS[2])),
      arguments("(languages==\"ukr\") sortby title", List.of(IDS[2])),

      arguments("(source==\"FOLIO\" and languages==\"ukr\") sortby title", List.of(IDS[2])),

      arguments(format("(id=* and instanceTypeId==%s) sortby title", TYPES[0]), List.of(IDS[1], IDS[2])),
      arguments(format("(id=* and instanceTypeId==%s) sortby title", TYPES[1]), List.of(IDS[0], IDS[3], IDS[4])),

      arguments(format("(id=* and statusId==%s) sortby title", STATUSES[0]), List.of(IDS[0], IDS[2])),
      arguments(format("(id=* and statusId==%s) sortby title", STATUSES[1]), List.of(IDS[1], IDS[3], IDS[4])),

      arguments(format("(id=* and instanceFormatIds==\"%s\") sortby title", FORMATS[0]), List.of(IDS[3])),
      arguments(format("(id=* and instanceFormatIds==%s) sortby title", FORMATS[1]),
        List.of(IDS[0], IDS[1], IDS[3], IDS[4])),
      arguments(format("(id=* and instanceFormatIds==%s) sortby title", FORMATS[2]), List.of(IDS[0], IDS[2], IDS[3])),
      arguments(format("(id=* and instanceFormatIds==(%s or %s)) sortby title", FORMATS[1], FORMATS[2]), List.of(IDS)),

      arguments("(id=* and staffSuppress==true) sortby title", List.of(IDS[0], IDS[1], IDS[2])),
      arguments("(id=* and staffSuppress==false) sortby title", List.of(IDS[3], IDS[4])),
      arguments("(staffSuppress==false) sortby title", List.of(IDS[3], IDS[4])),

      arguments("(id=* and discoverySuppress==true) sortby title", List.of(IDS[0], IDS[1])),
      arguments("(id=* and discoverySuppress==false) sortby title", List.of(IDS[2], IDS[3], IDS[4])),
      arguments("(discoverySuppress==false) sortby title", List.of(IDS[2], IDS[3], IDS[4])),
      arguments("(id=* and staffSuppress==true and discoverySuppress==false) sortby title", List.of(IDS[2])),

      arguments("(id=* and instanceTags==text) sortby title", List.of(IDS[0])),
      arguments("(id=* and instanceTags==science) sortby title", List.of(IDS[0], IDS[2])),
      arguments("(instanceTags==science) sortby title", List.of(IDS[0], IDS[2])),

      arguments(format("(id=* and item.effectiveLocationId==%s) sortby title", LOCATIONS[0]),
        List.of(IDS[0], IDS[2], IDS[3], IDS[4])),
      arguments(format("(id=* and item.effectiveLocationId==%s) sortby title", LOCATIONS[1]),
        List.of(IDS[1], IDS[2], IDS[4])),
      arguments(format("(item.effectiveLocationId==%s) sortby title", LOCATIONS[0]),
        List.of(IDS[0], IDS[2], IDS[3], IDS[4])),

      arguments(format("(normalizedDate1<%s) sortby normalizedDate1", DATES[2]), List.of(IDS[1], IDS[3])),
      arguments(format("(normalizedDate1>=%s and normalizedDate1<%s) sortby title", DATES[6], DATES[0]),
        List.of(IDS[1], IDS[2], IDS[3])),
      arguments(format("(normalizedDate1>=%s and normalizedDate1<%s) sortby normalizedDate1", DATES[6], DATES[0]),
        List.of(IDS[1], IDS[3], IDS[2])),
      arguments(format("(normalizedDate1>=%s) sortby title", DATES[0]), List.of(IDS[0], IDS[4])),
      arguments(format("(normalizedDate1>%s and normalizedDate1<%s) sortby normalizedDate1", DATES[6], DATES[7]),
        List.of(IDS[1], IDS[3])),

      arguments("(item.status.name==Available) sortby title", List.of(IDS[0], IDS[1], IDS[4])),
      arguments("(item.status.name==Missing) sortby title", List.of(IDS[2], IDS[3])),
      arguments("(item.status.name==\"Checked out\") sortby title", List.of(IDS[2], IDS[4])),
      arguments("(item.status.name==Available and source==MARC) sortby title", List.of(IDS[0], IDS[1])),

      arguments(format("(item.materialTypeId==%s) sortby title", MATERIAL_TYPES[0]), List.of(IDS[0], IDS[2])),
      arguments(format("(item.materialTypeId==%s) sortby title", MATERIAL_TYPES[1]), List.of(IDS[1], IDS[3], IDS[4])),

      arguments("item.discoverySuppress==true sortBy title", List.of(IDS[0], IDS[2])),
      arguments("item.discoverySuppress==false sortBy title", List.of(IDS[1], IDS[2], IDS[3], IDS[4])),

      arguments(format("(holdings.permanentLocationId==%s) sortby title", PERMANENT_LOCATIONS[0]),
        List.of(IDS[0], IDS[3])),
      arguments(format("(holdings.permanentLocationId==%s) sortby title", PERMANENT_LOCATIONS[1]),
        List.of(IDS[1], IDS[3])),
      arguments(format("(holdings.permanentLocationId==%s) sortby title", PERMANENT_LOCATIONS[2]),
        List.of(IDS[3], IDS[4])),

      arguments(format("(holdings.discoverySuppress==%s) sortby title", true), List.of(IDS[1], IDS[2])),
      arguments(format("(holdings.discoverySuppress==%s) sortby title", false), List.of(IDS[0], IDS[3], IDS[4])),

      arguments("(itemTags==itag1) sortby title", List.of(IDS[0], IDS[2])),
      arguments("(holdingsTags==htag1) sortby title", List.of(IDS[0], IDS[4])),

      arguments(format("(holdingsTypeId==%s) sortby title", HOLDINGS_TYPES[0]), List.of(IDS[0], IDS[3])),
      arguments(format("(holdingsTypeId==%s) sortby title", HOLDINGS_TYPES[1]), List.of(IDS[1], IDS[3])),

      arguments("(metadata.createdDate>= 2021-03-01) sortby title", List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(metadata.createdDate > 2021-03-01) sortby title", List.of(IDS[1], IDS[2], IDS[3])),
      arguments("(metadata.createdDate>= 2021-03-01 and metadata.createdDate < 2021-03-10) sortby title",
        List.of(IDS[0], IDS[2])),

      arguments("(metadata.updatedDate >= 2021-03-14) sortby title", List.of(IDS[2], IDS[3])),
      arguments("(metadata.updatedDate > 2021-03-01) sortby title", List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(metadata.updatedDate > 2021-03-05) sortby title", List.of(IDS[1], IDS[2], IDS[3])),
      arguments("(metadata.updatedDate < 2021-03-15) sortby title", List.of(IDS[0], IDS[1])),
      arguments("(metadata.updatedDate > 2021-03-14 and metadata.updatedDate < 2021-03-16) sortby title",
        List.of(IDS[2], IDS[3])),

      arguments("(holdings.metadata.createdDate>= 2021-03-01) sortby title", List.of(IDS[0], IDS[1], IDS[3])),
      arguments("(holdings.metadata.createdDate > 2021-03-01) sortby title", List.of(IDS[1], IDS[3])),
      arguments("(holdings.metadata.createdDate>= 2021-03-01 and metadata.createdDate < 2021-03-10) sortby title",
        List.of(IDS[0])),
      arguments("(holdings.metadata.createdDate>=2016-01-01 and holdings.metadata.createdDate<=2018-12-12) "
        + "sortby title", List.of()),
      arguments("(staffSuppress==false "
        + "and holdings.metadata.createdDate>=2016-01-01 and holdings.metadata.createdDate<=2018-12-12) "
        + "sortby title", List.of()),

      arguments("(holdings.metadata.updatedDate >= 2021-03-14) sortby title", List.of(IDS[3])),
      arguments("(holdings.metadata.updatedDate > 2021-03-01) sortby title", List.of(IDS[0], IDS[1], IDS[3])),
      arguments("(holdings.metadata.updatedDate > 2021-03-05) sortby title", List.of(IDS[1], IDS[3])),
      arguments("(holdings.metadata.updatedDate < 2021-03-15) sortby title", List.of(IDS[0], IDS[1], IDS[3])),
      arguments("(holdings.metadata.updatedDate > 2021-03-14 and metadata.updatedDate < 2021-03-16) sortby title",
        List.of(IDS[3])),

      arguments("(item.metadata.createdDate>= 2021-03-01) sortby title", List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(item.metadata.createdDate > 2021-03-01) sortby title", List.of(IDS[1], IDS[2], IDS[3])),
      arguments("(item.metadata.createdDate>= 2021-03-01 and metadata.createdDate < 2021-03-10) sortby title",
        List.of(IDS[0], IDS[2])),

      arguments("(item.metadata.updatedDate >= 2021-03-14) sortby title", List.of(IDS[2], IDS[3])),
      arguments("(item.metadata.updatedDate > 2021-03-01) sortby title", List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(item.metadata.updatedDate > 2021-03-05) sortby title", List.of(IDS[1], IDS[2], IDS[3])),
      arguments("(item.metadata.updatedDate < 2021-03-15) sortby title", List.of(IDS[0], IDS[1], IDS[3])),
      arguments("(item.metadata.updatedDate > 2021-03-14 and metadata.updatedDate < 2021-03-16) sortby title",
        List.of(IDS[2], IDS[3])),

      arguments("statisticalCodes == b5968c9e-cddc-4576-99e3-8e60aed8b0dd", List.of(IDS[0])),
      arguments("statisticalCodes == a2b01891-c9ab-4d04-8af8-8989af1c6aad", List.of(IDS[3])),
      arguments("statisticalCodes == 615e9911-edb1-4ab3-a9c3-a461a3de02f8", List.of(IDS[1])),
      arguments("statisticalCodes == unknown", emptyList()),
      arguments("statisticalCodeIds == b5968c9e-cddc-4576-99e3-8e60aed8b0dd", List.of(IDS[0])),
      arguments("statisticalCodeIds == a2b01891-c9ab-4d04-8af8-8989af1c6aad", emptyList()),
      arguments("holdings.statisticalCodeIds == b5968c9e-cddc-4576-99e3-8e60aed8b0dd", emptyList()),
      arguments("holdings.statisticalCodeIds == a2b01891-c9ab-4d04-8af8-8989af1c6aad", List.of(IDS[3])),
      arguments("item.statisticalCodeIds == b5968c9e-cddc-4576-99e3-8e60aed8b0dd", emptyList()),
      arguments("item.statisticalCodeIds == 615e9911-edb1-4ab3-a9c3-a461a3de02f8", List.of(IDS[1])),

      // Search by item filter (Backward compatibility)
      arguments(format("(id=* and items.effectiveLocationId==%s) sortby title", LOCATIONS[0]),
        List.of(IDS[0], IDS[2], IDS[3], IDS[4])),
      arguments(format("(id=* and items.effectiveLocationId==%s) sortby title", LOCATIONS[1]),
        List.of(IDS[1], IDS[2], IDS[4])),
      arguments(format("(items.effectiveLocationId==%s) sortby title", LOCATIONS[0]),
        List.of(IDS[0], IDS[2], IDS[3], IDS[4])),

      arguments("(items.status.name==Available) sortby title", List.of(IDS[0], IDS[1], IDS[4])),
      arguments("(items.status.name==Missing) sortby title", List.of(IDS[2], IDS[3])),
      arguments("(items.status.name==\"Checked out\") sortby title", List.of(IDS[2], IDS[4])),
      arguments("(items.status.name==Available and source==MARC) sortby title", List.of(IDS[0], IDS[1])),

      arguments(format("(items.materialTypeId==%s) sortby title", MATERIAL_TYPES[0]), List.of(IDS[0], IDS[2])),
      arguments(format("(items.materialTypeId==%s) sortby title", MATERIAL_TYPES[1]), List.of(IDS[1], IDS[3], IDS[4])),

      arguments("items.discoverySuppress==true sortBy title", List.of(IDS[0], IDS[2])),
      arguments("items.discoverySuppress==false sortBy title", List.of(IDS[1], IDS[2], IDS[3], IDS[4])),

      arguments("items.statisticalCodeIds == b5968c9e-cddc-4576-99e3-8e60aed8b0dd", emptyList()),
      arguments("items.statisticalCodeIds == 615e9911-edb1-4ab3-a9c3-a461a3de02f8", List.of(IDS[1])),

      arguments("(items.metadata.createdDate>= 2021-03-01) sortby title", List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(items.metadata.createdDate > 2021-03-01) sortby title", List.of(IDS[1], IDS[2], IDS[3])),
      arguments("(items.metadata.createdDate>= 2021-03-01 and metadata.createdDate < 2021-03-10) sortby title",
        List.of(IDS[0], IDS[2])),
      arguments("(items.metadata.createdDate>=2016-01-01 and items.metadata.createdDate<=2018-12-12) sortby title",
        List.of()),
      arguments("(staffSuppress==false "
          + "and items.metadata.createdDate>=2016-01-01 and items.metadata.createdDate<=2018-12-12) sortby title",
        List.of()),

      arguments("(items.metadata.updatedDate >= 2021-03-14) sortby title", List.of(IDS[2], IDS[3])),
      arguments("(items.metadata.updatedDate > 2021-03-01) sortby title", List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(items.metadata.updatedDate > 2021-03-05) sortby title", List.of(IDS[1], IDS[2], IDS[3])),
      arguments("(items.metadata.updatedDate < 2021-03-15) sortby title", List.of(IDS[0], IDS[1], IDS[3])),
      arguments("(items.metadata.updatedDate > 2021-03-14 and metadata.updatedDate < 2021-03-16) sortby title",
        List.of(IDS[2], IDS[3])),
      arguments(format("(classifications.classificationTypeId==%s) sortby title", CLASSIFICATION_TYPE_IDS[0]),
        List.of(IDS[0])),
      arguments(format("(classifications.classificationTypeId==(%s or %s)) sortby title",
        CLASSIFICATION_TYPE_IDS[0], CLASSIFICATION_TYPE_IDS[1]), List.of(IDS[0], IDS[1]))
    );
  }

  private static Stream<Arguments> invalidDateSearchQueriesProvider() {
    return Stream.of(
      arguments("metadata.createdDate", "2022-6-27"),
      arguments("metadata.updatedDate", "2022-06-1"),

      arguments("holdings.metadata.createdDate", "2022-15-01"),
      arguments("holdings.metadata.updatedDate", "2022-06-40"),

      arguments("item.metadata.updatedDate", "invalidDate")
    );
  }

  private static Stream<Arguments> facetQueriesProvider() {
    var allFacets = array("discoverySuppress", "staffSuppress", "languages", "instanceTags", "source",
      "instanceTypeId", "statusId", "instanceFormatIds", "items.effectiveLocationId", "items.status.name",
      "holdingsTypeId", "holdings.permanentLocationId", "holdings.discoverySuppress", "items.materialTypeId",
      "tenantId", "shared");
    return Stream.of(
      arguments("id=*", allFacets, mapOf(
        "shared", facet(facetItem("false", 5)),
        "tenantId", facet(facetItem(TENANT_ID, 5)),
        "discoverySuppress", facet(facetItem("false", 3), facetItem("true", 2)),
        "staffSuppress", facet(facetItem("true", 3), facetItem("false", 2)),
        "languages", facet(facetItem("eng", 3), facetItem("fra", 2), facetItem("ita", 2),
          facetItem("ger", 1), facetItem("rus", 1), facetItem("ukr", 1)),
        "instanceTags", facet(facetItem("cooking", 2), facetItem("future", 2), facetItem("science", 2),
          facetItem("casual", 1), facetItem("text", 1)),
        "source", facet(facetItem("MARC", 3), facetItem("FOLIO", 2)),
        "instanceTypeId", facet(facetItem(TYPES[1], 3), facetItem(TYPES[0], 2)),
        "statusId", facet(facetItem(STATUSES[1], 3), facetItem(STATUSES[0], 2)),
        "instanceFormatIds", facet(facetItem(FORMATS[1], 4), facetItem(FORMATS[2], 3), facetItem(FORMATS[0], 1)),

        "items.effectiveLocationId", facet(facetItem(LOCATIONS[0], 4), facetItem(LOCATIONS[1], 3)),
        "items.status.name", facet(facetItem("Available", 3), facetItem("Checked out", 2), facetItem("Missing", 2)),
        "items.materialTypeId", facet(facetItem(MATERIAL_TYPES[0], 2), facetItem(MATERIAL_TYPES[1], 3)),

        "holdingsTypeId", facet(facetItem(HOLDINGS_TYPES[0], 2), facetItem(HOLDINGS_TYPES[1], 2)),
        "holdings.permanentLocationId", facet(facetItem(PERMANENT_LOCATIONS[1], 2),
          facetItem(PERMANENT_LOCATIONS[0], 2), facetItem(PERMANENT_LOCATIONS[2], 2)),
        "holdings.discoverySuppress", facet(facetItem("false", 3), facetItem("true", 2))
      )),

      arguments("id=*", array("source"), mapOf("source", facet(facetItem("MARC", 3), facetItem("FOLIO", 2)))),

      arguments("id=*", array("languages"), mapOf("languages", facet(facetItem("eng", 3), facetItem("fra", 2),
        facetItem("ita", 2), facetItem("ger", 1), facetItem("rus", 1), facetItem("ukr", 1)))),

      arguments("id=*", array("languages:2"), mapOf("languages", facet(facetItem("eng", 3), facetItem("fra", 2)))),

      arguments("languages==eng", array("languages:2"), mapOf(
        "languages", facet(facetItem("eng", 3), facetItem("fra", 2)))),

      arguments("languages==(rus or ukr)", array("languages:4"), mapOf(
        "languages", facet(facetItem("rus", 1), facetItem("ukr", 1), facetItem("eng", 3), facetItem("fra", 2)))),

      arguments("languages==(\"eng\" or \"fra\")", array("languages:5"), mapOf(
        "languages", facet(facetItem("eng", 3), facetItem("fra", 2), facetItem("ita", 2),
          facetItem("ger", 1), facetItem("rus", 1)))),

      arguments("id=*", array("discoverySuppress"), mapOf(
        "discoverySuppress", facet(facetItem("false", 3), facetItem("true", 2)))),

      arguments("id=*", array("staffSuppress"), mapOf(
        "staffSuppress", facet(facetItem("true", 3), facetItem("false", 2)))),

      arguments("id=*", array("instanceTags"), mapOf("instanceTags", facet(
        facetItem("cooking", 2), facetItem("future", 2), facetItem("science", 2),
        facetItem("casual", 1), facetItem("text", 1)))),

      arguments("id=*", array("instanceTags:3"), mapOf("instanceTags", facet(
        facetItem("cooking", 2), facetItem("future", 2), facetItem("science", 2)))),

      arguments("id=*", array("instanceTypeId"), mapOf("instanceTypeId", facet(
        facetItem(TYPES[1], 3), facetItem(TYPES[0], 2)))),

      arguments("id=*", array("statusId"), mapOf("statusId", facet(
        facetItem(STATUSES[1], 3), facetItem(STATUSES[0], 2)))),

      arguments("id=*", array("instanceFormatIds"), mapOf("instanceFormatIds", facet(
        facetItem(FORMATS[1], 4), facetItem(FORMATS[2], 3), facetItem(FORMATS[0], 1)))),

      arguments("instanceFormatIds==" + FORMATS[0], array("instanceFormatIds"), mapOf(
        "instanceFormatIds", facet(facetItem(FORMATS[1], 4), facetItem(FORMATS[2], 3), facetItem(FORMATS[0], 1)))),

      arguments("source==MARC", array("instanceFormatIds"), mapOf(
        "instanceFormatIds", facet(facetItem(FORMATS[0], 1), facetItem(FORMATS[1], 3), facetItem(FORMATS[2], 2)))),

      arguments("id=*", array("items.effectiveLocationId"), mapOf(
        "items.effectiveLocationId", facet(facetItem(LOCATIONS[0], 4), facetItem(LOCATIONS[1], 3)))),

      arguments("source==MARC", array("source", "items.effectiveLocationId"), mapOf(
        "source", facet(facetItem("MARC", 3), facetItem("FOLIO", 2)),
        "items.effectiveLocationId", facet(facetItem(LOCATIONS[0], 2), facetItem(LOCATIONS[1], 1)))),

      arguments("id=*", array("items.status.name"), mapOf(
        "items.status.name", facet(facetItem("Available", 3), facetItem("Checked out", 2), facetItem("Missing", 2)))),

      arguments("id=*", array("items.discoverySuppress"), mapOf(
        "items.discoverySuppress", facet(facetItem("true", 2), facetItem("false", 4)))),

      arguments("id=*", array("items.statisticalCodeIds"), mapOf(
        "items.statisticalCodeIds", facet(facetItem("615e9911-edb1-4ab3-a9c3-a461a3de02f8", 1)))),

      arguments("id=*", array("item.effectiveLocationId"), mapOf(
        "items.effectiveLocationId", facet(facetItem(LOCATIONS[0], 4), facetItem(LOCATIONS[1], 3)))),

      arguments("id=*", array("item.status.name"), mapOf(
        "items.status.name", facet(facetItem("Available", 3), facetItem("Checked out", 2), facetItem("Missing", 2)))),

      arguments("id=*", array("item.discoverySuppress"), mapOf(
        "items.discoverySuppress", facet(facetItem("true", 2), facetItem("false", 4)))),

      arguments("id=*", array("item.statisticalCodeIds"), mapOf(
        "items.statisticalCodeIds", facet(facetItem("615e9911-edb1-4ab3-a9c3-a461a3de02f8", 1)))),

      arguments("id=*", array("itemTags"), mapOf(
        "itemTags", facet(facetItem("itag3", 4), facetItem("itag1", 2), facetItem("itag2", 2)))),

      arguments("id=*", array("holdings.permanentLocationId"), mapOf(
        "holdings.permanentLocationId", facet(facetItem(PERMANENT_LOCATIONS[1], 2),
          facetItem(PERMANENT_LOCATIONS[0], 2), facetItem(PERMANENT_LOCATIONS[2], 2)))),

      arguments("id=*", array("holdings.discoverySuppress"), mapOf(
        "holdings.discoverySuppress", facet(facetItem("false", 3),
          facetItem("true", 2)))),

      arguments("id=*", array("holdingsTags"), mapOf(
        "holdingsTags", facet(facetItem("htag2", 3), facetItem("htag1", 2), facetItem("htag3", 2)))),

      arguments("id=*", array("holdingsTypeId"), mapOf(
        "holdingsTypeId", facet(facetItem(HOLDINGS_TYPES[0], 2), facetItem(HOLDINGS_TYPES[1], 2)))),

      arguments("id=*", array("statisticalCodes"), mapOf(
        "statisticalCodes", facet(facetItem("b5968c9e-cddc-4576-99e3-8e60aed8b0dd", 1),
          facetItem("a2b01891-c9ab-4d04-8af8-8989af1c6aad", 1), facetItem("615e9911-edb1-4ab3-a9c3-a461a3de02f8", 1)))),

      arguments("id=*", array("statisticalCodeIds"), mapOf(
        "statisticalCodeIds", facet(facetItem("b5968c9e-cddc-4576-99e3-8e60aed8b0dd", 1)))),

      arguments("id=*", array("holdings.statisticalCodeIds"), mapOf(
        "holdings.statisticalCodeIds", facet(facetItem("a2b01891-c9ab-4d04-8af8-8989af1c6aad", 1)))),

      arguments("id=*", array("holdings.sourceId"), mapOf("holdings.sourceId", facet(facetItem("FOLIO", 1))))
    );
  }

  private static Instance[] instances() {
    var instances = IntStream.range(0, 5)
      .mapToObj(i -> new Instance().id(IDS[i]).title("Resource" + i))
      .toArray(Instance[]::new);

    instances[0]
      .tenantId(TENANT_ID)
      .source("MARC")
      .languages(List.of("eng", "ita"))
      .instanceTypeId(TYPES[1])
      .statusId(STATUSES[0])
      .staffSuppress(true)
      .discoverySuppress(true)
      .instanceFormatIds(List.of(FORMATS[1], FORMATS[2]))
      .tags(tags("text", "science"))
      .statisticalCodeIds(singletonList("b5968c9e-cddc-4576-99e3-8e60aed8b0dd"))
      .metadata(metadata("2021-03-01T00:00:00.000+00:00", "2021-03-05T12:30:00.000+00:00"))
      .dates(new Dates().date1(DATES[0]))
      .items(List.of(
        new Item().id(ITEM_IDS[0])
          .effectiveLocationId(LOCATIONS[0]).status(itemStatus(AVAILABLE))
          .holdingsRecordId(HOLDINGS_IDS[0])
          .discoverySuppress(true)
          .materialTypeId(MATERIAL_TYPES[0])
          .metadata(metadata("2021-03-01T00:00:00.000+00:00", "2021-03-05T12:30:00.000+00:00"))
          .tags(tags("itag1", "itag3"))))
      .holdings(List.of(
        new Holding().id(HOLDINGS_IDS[0])
          .holdingsTypeId(HOLDINGS_TYPES[0])
          .metadata(metadata("2021-03-01T00:00:00.000+00:00", "2021-03-05T12:30:00.000+00:00"))
          .permanentLocationId(PERMANENT_LOCATIONS[0]).tags(tags("htag1", "htag2"))))
      .addClassificationsItem(new Classification()
        .classificationTypeId(CLASSIFICATION_TYPE_IDS[0])
        .classificationNumber("QA76.73.C15"));

    instances[1]
      .tenantId(TENANT_ID)
      .source("MARC")
      .languages(List.of("eng", "ger", "fra"))
      .instanceTypeId(TYPES[0])
      .statusId(STATUSES[1])
      .staffSuppress(true)
      .discoverySuppress(true)
      .instanceFormatIds(List.of(FORMATS[1]))
      .tags(tags("future"))
      .metadata(metadata("2021-03-10T01:00:00.000+00:00", "2021-03-12T15:40:00.000+00:00"))
      .dates(new Dates().date1(DATES[1]))
      .items(List.of(
        new Item().id(ITEM_IDS[1])
          .holdingsRecordId(HOLDINGS_IDS[1])
          .effectiveLocationId(LOCATIONS[1]).status(itemStatus(AVAILABLE))
          .discoverySuppress(false)
          .materialTypeId(MATERIAL_TYPES[1])
          .statisticalCodeIds(singletonList("615e9911-edb1-4ab3-a9c3-a461a3de02f8"))
          .metadata(metadata("2021-03-10T01:00:00.000+00:00", "2021-03-12T15:40:00.000+00:00"))
          .tags(tags("itag2", "itag3"))))
      .holdings(List.of(new Holding().id(HOLDINGS_IDS[1]).discoverySuppress(true)
        .holdingsTypeId(HOLDINGS_TYPES[1])
        .metadata(metadata("2021-03-10T01:00:00.000+00:00", "2021-03-12T15:40:00.000+00:00"))
        .permanentLocationId(PERMANENT_LOCATIONS[1]).tags(tags("htag2", "htag3"))))
      .addClassificationsItem(new Classification()
        .classificationTypeId(CLASSIFICATION_TYPE_IDS[1])
        .classificationNumber("TK5105.88815"));

    instances[2]
      .tenantId(TENANT_ID)
      .source("FOLIO")
      .languages(List.of("rus", "ukr"))
      .instanceTypeId(TYPES[0])
      .statusId(STATUSES[0])
      .staffSuppress(true)
      .instanceFormatIds(List.of(FORMATS[2]))
      .tags(tags("future", "science"))
      .metadata(metadata("2021-03-08T15:00:00.000+00:00", "2021-03-15T22:30:00.000+00:00"))
      .dates(new Dates().date1(DATES[2]))
      .items(List.of(
        new Item().id(ITEM_IDS[2])
          .holdingsRecordId(HOLDINGS_IDS[2])
          .effectiveLocationId(LOCATIONS[0]).status(itemStatus(MISSING))
          .metadata(metadata("2021-03-08T15:00:00.000+00:00", "2021-03-15T22:30:00.000+00:00"))
          .discoverySuppress(true).materialTypeId(MATERIAL_TYPES[0]).tags(tags("itag3")),
        new Item().id(ITEM_IDS[3])
          .holdingsRecordId(HOLDINGS_IDS[2])
          .effectiveLocationId(LOCATIONS[1]).status(itemStatus(CHECKED_OUT))
          .tags(tags("itag1", "itag2", "itag3"))))
      .holdings(List.of(new Holding().id(HOLDINGS_IDS[2]).discoverySuppress(true)));

    instances[3]
      .tenantId(TENANT_ID)
      .source("MARC")
      .languages(List.of("ita"))
      .staffSuppress(false)
      .discoverySuppress(false)
      .instanceTypeId(TYPES[1])
      .statusId(STATUSES[1])
      .instanceFormatIds(List.of(FORMATS))
      .tags(tags("casual", "cooking"))
      .metadata(metadata("2021-03-15T12:00:00.000+00:00", "2021-03-15T12:00:00.000+00:00"))
      .dates(new Dates().date1(DATES[3]))
      .items(List.of(new Item().id(ITEM_IDS[4])
        .holdingsRecordId(HOLDINGS_IDS[3])
        .effectiveLocationId(LOCATIONS[0]).status(itemStatus(MISSING))
        .metadata(metadata("2014-03-15T12:00:00.000+00:00", "2014-03-15T12:00:00.000+00:00"))
        .materialTypeId(MATERIAL_TYPES[1]),
        new Item().id(ITEM_IDS[7])
          .holdingsRecordId(HOLDINGS_IDS[4])
          .effectiveLocationId(LOCATIONS[0]).status(itemStatus(MISSING))
          .metadata(metadata("2024-03-15T12:00:00.000+00:00", "2024-03-15T12:00:00.000+00:00"))
          .materialTypeId(MATERIAL_TYPES[1])))
      .holdings(List.of(
        new Holding().id(HOLDINGS_IDS[3]).permanentLocationId(PERMANENT_LOCATIONS[0])
          .holdingsTypeId(HOLDINGS_TYPES[1])
          .metadata(metadata("2014-03-15T12:00:00.000+00:00", "2014-03-15T12:00:00.000+00:00"))
          .sourceId("FOLIO").statisticalCodeIds(singletonList("a2b01891-c9ab-4d04-8af8-8989af1c6aad")),
        new Holding().id(HOLDINGS_IDS[4]).permanentLocationId(PERMANENT_LOCATIONS[1])
          .holdingsTypeId(HOLDINGS_TYPES[0])
          .metadata(metadata("2024-03-15T12:00:00.000+00:00", "2024-03-15T12:00:00.000+00:00"))
          .tags(tags("htag2")),
        new Holding().id(HOLDINGS_IDS[5]).permanentLocationId(PERMANENT_LOCATIONS[2]).tags(tags("htag3"))));

    instances[4]
      .tenantId(TENANT_ID)
      .source("FOLIO")
      .languages(List.of("eng", "fra"))
      .instanceTypeId(TYPES[1])
      .statusId(STATUSES[1])
      .instanceFormatIds(List.of(FORMATS[1]))
      .tags(tags("cooking"))
      .dates(new Dates().date1(DATES[4]).date2(DATES[4]))
      .items(List.of(
        new Item().id(ITEM_IDS[5]).holdingsRecordId(HOLDINGS_IDS[6])
          .effectiveLocationId(LOCATIONS[0]).status(itemStatus(CHECKED_OUT)).tags(tags("itag3")),
        new Item().id(ITEM_IDS[6]).holdingsRecordId(HOLDINGS_IDS[6])
          .effectiveLocationId(LOCATIONS[1]).status(itemStatus(AVAILABLE))
          .materialTypeId(MATERIAL_TYPES[1])))
      .holdings(List.of(new Holding().id(HOLDINGS_IDS[6])
        .permanentLocationId(PERMANENT_LOCATIONS[2]).tags(tags("htag1"))));

    return instances;
  }

  private static ItemStatus itemStatus(String itemStatus) {
    return new ItemStatus().name(itemStatus);
  }

  private static Tags tags(String... tags) {
    return new Tags().tagList(asList(tags));
  }

  private static Metadata metadata(String createdDate, String updatedDate) {
    return new Metadata().createdDate(createdDate).updatedDate(updatedDate);
  }
}
