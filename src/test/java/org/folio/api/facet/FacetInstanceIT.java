package org.folio.api.facet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.RecordType;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@IntegrationTest
public abstract class FacetInstanceIT extends BaseSharedTest {

  private static final String DISCOVERY_SUPPRESS_FACET = "discoverySuppress";
  private static final String HOLDINGS_DISCOVERY_SUPPRESS_FACET = "holdings.discoverySuppress";
  private static final String HOLDINGS_PERMANENT_LOCATION_ID_FACET = "holdings.permanentLocationId";
  private static final String HOLDINGS_SOURCE_ID_FACET = "holdings.sourceId";
  private static final String HOLDINGS_STATISTICAL_CODE_IDS_FACET = "holdings.statisticalCodeIds";
  private static final String HOLDINGS_TAGS_FACET = "holdingsTags";
  private static final String HOLDINGS_TYPE_ID_FACET = "holdingsTypeId";
  private static final String INSTANCE_FORMAT_IDS_FACET = "instanceFormatIds";
  private static final String INSTANCE_TAGS_FACET = "instanceTags";
  private static final String INSTANCE_TYPE_ID_FACET = "instanceTypeId";
  private static final String ITEM_TAGS_FACET = "itemTags";
  private static final String ITEMS_DISCOVERY_SUPPRESS_FACET = "items.discoverySuppress";
  private static final String ITEMS_EFFECTIVE_LOCATION_ID_FACET = "items.effectiveLocationId";
  private static final String ITEMS_MATERIAL_TYPE_ID_FACET = "items.materialTypeId";
  private static final String ITEMS_STATISTICAL_CODE_IDS_FACET = "items.statisticalCodeIds";
  private static final String ITEMS_STATUS_NAME_FACET = "items.status.name";
  private static final String LANGUAGES_FACET = "languages";
  private static final String SHARED_FACET = "shared";
  private static final String SOURCE_FACET = "source";
  private static final String STAFF_SUPPRESS_FACET = "staffSuppress";
  private static final String STATISTICAL_CODE_IDS_FACET = "statisticalCodeIds";
  private static final String STATISTICAL_CODES_FACET = "statisticalCodes";
  private static final String STATUS_ID_FACET = "statusId";
  private static final String TENANT_ID_FACET = "tenantId";

  private static final String[] FORMATS = array(
    "89f6d4f0-9cd2-4015-828d-331dc3adb47a",
    "25a81102-a2a9-4576-85ff-133ebcbcef2c",
    "e57e36a3-80ff-46a6-ac2f-5c8bd79bc2bb",
    "7f9c4ac0-fa3d-43b7-b978-3bf0be38c4da");
  private static final String[] TYPES = array(
    "24da24dd-03ae-4e34-bad6-c79e342baeb9",
    "de9e38bb-89a8-43ee-922f-c973b122cbb3",
    "6312d172-f0cf-40f6-b27d-9fa8feaf332f",
    "497b5090-3da2-486c-b57f-de5bb3c2e26d",
    "535e3160-763a-42f9-b0c0-d8ed7df6e2a2",
    "a2c91e87-6bab-44d6-8adb-1fd02481fc4f",
    "225faa14-f9bf-4ecd-990d-69433c912434",
    "3be24c14-3551-4180-9292-26a786649c8b",
    "df5dddff-9c30-4507-8b82-119ff972d4d7",
    "9bce18bd-45bf-4949-8fa8-63163e4b7d7f");
  private static final String[] LOCATIONS = array(
    "ce23dfa1-17e8-4a1f-ad6b-34ce6ab352c2",
    "f1a49577-5096-4771-a8a0-d07d642241eb",
    "65b6c2e9-8a7b-4a10-9b5d-ba1cf0313cd7",
    "b777f3a4-4372-4792-a87d-8e8f177eab10",
    "0d106980-1789-42ac-b355-a6c7a74ddea3",
    "fcd64ce1-6995-48f0-840e-89ffa2288371",
    "184aae84-a5bf-4c6a-85ba-4a7c73026cd5",
    "53cf956f-c1df-410b-8bea-27f712cca7c0",
    "f34d27c6-a8eb-461b-acd6-5dea81771e70",
    "4fdca025-1629-4688-aeb7-9c5fe5c73549",
    "b241764c-1466-4e1d-a028-1a3684a5da87",
    "cdd60388-0c75-4969-b3c5-2d04621ed26f");
  private static final String[] PERMANENT_LOCATIONS = array(
    "765b4c3b-485c-4ce4-a117-f99c01ac49fe",
    "4fdca025-1629-4688-aeb7-9c5fe5c73549",
    "81f1ab2c-83c5-4a90-a8b7-c8c8179c0697",
    "fcd64ce1-6995-48f0-840e-89ffa2288371",
    "53cf956f-c1df-410b-8bea-27f712cca7c0",
    "b241764c-1466-4e1d-a028-1a3684a5da87",
    "f34d27c6-a8eb-461b-acd6-5dea81771e70",
    "0d106980-1789-42ac-b355-a6c7a74ddea3",
    "758258bc-ecc1-41b8-abca-f7b610822ffd",
    "65b6c2e9-8a7b-4a10-9b5d-ba1cf0313cd7",
    "184aae84-a5bf-4c6a-85ba-4a7c73026cd5");
  private static final String[] MATERIAL_TYPES = array(
    "c898029e-9a02-4b61-bedb-6956cff21bc2",
    "3d413322-1dee-431b-bd73-b1e399063260",
    "1a54b431-2e4f-452d-9cae-9cee66c9a892",
    "d9acad2f-2aac-4b48-9097-e6ab85906b25",
    "615b8413-82d5-4203-aa6e-e37984cb5ac3",
    "30b3e36a-d3b2-415e-98c2-47fbdf878862",
    "5ee11d91-f7e8-481d-b079-65d708582ccc",
    "fd6c6515-d470-4561-9c32-3e3290d4ca98",
    "dd0bf600-dbd9-44ab-9ff2-e2a61a6539f1");
  private static final String[] STATUSES = array(
    "54cb0be0-2b5b-4da5-a687-32dec54b016a",
    "1117f093-0bfd-4324-aa3f-96c77f43b2bf",
    "9634a5ab-9228-4703-baf2-4d12ebc77d56"
  );
  private static final String[] HOLDINGS_TYPES = array(
    "eb003b9d-86f2-4bdf-9f8e-28851122617d",
    "d02bb1e2-fa7f-4354-a9f4-1ca9b81510a2",
    "0c422f92-0f4d-4d32-8cbe-390ebc33a3e5",
    "03c9c400-b9e3-4a07-ac0e-05ab470233ed",
    "e6da6c98-6dd0-41bc-8b4b-cfd4bbd9c3ae",
    "996f93e2-5b5e-4cf2-9168-33ced1f95eed",
    "dc35d0ae-e877-488b-8e97-6e41444e6d0a");

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForInstances_parameterized")
  void getFacetsForInstances_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(doGet(recordFacetsPath(RecordType.INSTANCES, query, facets)), FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      assertNotNull(actual.getFacets());
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).as("Facet %s exists", facetName).isNotNull();
      assertThat(actualFacet.getValues()).as("Facet %s has expected values", facetName)
        .containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
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

  @SuppressWarnings("checkstyle:MethodLength")
  private static Stream<Arguments> facetQueriesProvider() {
    var allFacets = array(
      DISCOVERY_SUPPRESS_FACET,
      HOLDINGS_DISCOVERY_SUPPRESS_FACET,
      HOLDINGS_PERMANENT_LOCATION_ID_FACET,
      HOLDINGS_SOURCE_ID_FACET,
      HOLDINGS_STATISTICAL_CODE_IDS_FACET,
      HOLDINGS_TAGS_FACET,
      HOLDINGS_TYPE_ID_FACET,
      INSTANCE_FORMAT_IDS_FACET,
      INSTANCE_TAGS_FACET,
      INSTANCE_TYPE_ID_FACET,
      ITEM_TAGS_FACET,
      ITEMS_DISCOVERY_SUPPRESS_FACET,
      ITEMS_EFFECTIVE_LOCATION_ID_FACET,
      ITEMS_MATERIAL_TYPE_ID_FACET,
      ITEMS_STATISTICAL_CODE_IDS_FACET,
      ITEMS_STATUS_NAME_FACET,
      LANGUAGES_FACET,
      SHARED_FACET,
      SOURCE_FACET,
      STAFF_SUPPRESS_FACET,
      STATISTICAL_CODE_IDS_FACET,
      STATISTICAL_CODES_FACET,
      STATUS_ID_FACET,
      TENANT_ID_FACET
    );
    return Stream.of(
      arguments("id=*", allFacets, mapOf(
        DISCOVERY_SUPPRESS_FACET, composeDiscoverySuppressFacet(),
        HOLDINGS_DISCOVERY_SUPPRESS_FACET, composeHoldingsDiscoverySuppressFacet(),
        HOLDINGS_PERMANENT_LOCATION_ID_FACET, composeHoldingsPermanentLocationFacet(),
        HOLDINGS_SOURCE_ID_FACET, composeHoldingsSourceFacet(),
        HOLDINGS_STATISTICAL_CODE_IDS_FACET, composeHoldingsStatisticalCodeIdFacet(),
        HOLDINGS_TAGS_FACET, composeHoldingsTagFacet(),
        HOLDINGS_TYPE_ID_FACET, composeHoldingsTypeFacet(),
        INSTANCE_FORMAT_IDS_FACET, composeInstanceFormatsFacet(),
        INSTANCE_TAGS_FACET, composeInstanceTagsFacet(),
        INSTANCE_TYPE_ID_FACET, composeInstanceTypeFacet(),
        ITEM_TAGS_FACET, composeItemTagFacet(),
        ITEMS_DISCOVERY_SUPPRESS_FACET, composeItemDiscoverySuppressFacet(),
        ITEMS_EFFECTIVE_LOCATION_ID_FACET, composeEffectiveLocationFacet(),
        ITEMS_MATERIAL_TYPE_ID_FACET, composeItemMaterialTypeFacet(),
        ITEMS_STATISTICAL_CODE_IDS_FACET, composeItemStatisticalCodeFacet(),
        ITEMS_STATUS_NAME_FACET, composeItemStatusFacet(),
        LANGUAGES_FACET, composeLanguagesFacet(),
        SHARED_FACET, composeSharedFacet(),
        SOURCE_FACET, composeSourceFacet(),
        STAFF_SUPPRESS_FACET, composeStaffSuppressFacet(),
        STATISTICAL_CODE_IDS_FACET, composeStatisticalCodeIdFacet(),
        STATISTICAL_CODES_FACET, composeStatisticatlCodeFacet(),
        STATUS_ID_FACET, composeStatusFacet(),
        TENANT_ID_FACET, composeTenantFacet()
      )),

      arguments("id=*", array(SOURCE_FACET), mapOf(SOURCE_FACET, composeSourceFacet())),
      arguments("id=*", array(LANGUAGES_FACET), mapOf(LANGUAGES_FACET, composeLanguagesFacet())),
      arguments("id=*", array(LANGUAGES_FACET + ":2"),
        mapOf(LANGUAGES_FACET, facet(facetItem("eng", 94), facetItem("fra", 2)))),
      arguments("languages==eng", array(LANGUAGES_FACET + ":2"), mapOf(
        LANGUAGES_FACET, facet(facetItem("eng", 94), facetItem("fra", 2)))),
      arguments("languages==(rus or ukr)", array(LANGUAGES_FACET + ":4"), mapOf(
        LANGUAGES_FACET, facet(facetItem("rus", 1), facetItem("ukr", 1), facetItem("eng", 94), facetItem("fra", 2)))),
      arguments("languages==(\"eng\" or \"fra\")", array(LANGUAGES_FACET + ":5"), mapOf(
        LANGUAGES_FACET, facet(facetItem("eng", 94), facetItem("fra", 2), facetItem("ita", 2),
          facetItem("ger", 1), facetItem("rus", 1)))),
      arguments("id=*", array(DISCOVERY_SUPPRESS_FACET),
        mapOf(DISCOVERY_SUPPRESS_FACET, composeDiscoverySuppressFacet())),

      arguments("id=*", array(STAFF_SUPPRESS_FACET), mapOf(STAFF_SUPPRESS_FACET, composeStaffSuppressFacet())),

      arguments("id=*", array(INSTANCE_TAGS_FACET), mapOf(INSTANCE_TAGS_FACET, composeInstanceTagsFacet())),

      arguments("id=*", array(INSTANCE_TAGS_FACET + ":3"), mapOf(INSTANCE_TAGS_FACET, facet(
        facetItem("sort-titles", 13), facetItem("ebook-available", 11), facetItem("high-demand", 11)))),

      arguments("id=*", array(INSTANCE_TYPE_ID_FACET), mapOf(INSTANCE_TYPE_ID_FACET, composeInstanceTypeFacet())),

      arguments("id=*", array(STATUS_ID_FACET), mapOf(STATUS_ID_FACET, composeStatusFacet())),

      arguments("id=*", array(INSTANCE_FORMAT_IDS_FACET),
        mapOf(INSTANCE_FORMAT_IDS_FACET, composeInstanceFormatsFacet())),

      arguments("instanceFormatIds==" + FORMATS[0], array(INSTANCE_FORMAT_IDS_FACET), mapOf(
        INSTANCE_FORMAT_IDS_FACET, composeInstanceFormatsFacet())),

      arguments("source==MARC", array(INSTANCE_FORMAT_IDS_FACET), mapOf(
        INSTANCE_FORMAT_IDS_FACET,
        facet(facetItem(FORMATS[0], 1), facetItem(FORMATS[1], 3), facetItem(FORMATS[2], 2)))),

      arguments("id=*", array(ITEMS_EFFECTIVE_LOCATION_ID_FACET),
        mapOf(ITEMS_EFFECTIVE_LOCATION_ID_FACET, composeEffectiveLocationFacet())),

      arguments("source==MARC", array(SOURCE_FACET, ITEMS_EFFECTIVE_LOCATION_ID_FACET), mapOf(
        SOURCE_FACET, composeSourceFacet(),
        ITEMS_EFFECTIVE_LOCATION_ID_FACET, facet(facetItem(LOCATIONS[0], 2), facetItem(LOCATIONS[1], 1)))),

      arguments("id=*", array(ITEMS_STATUS_NAME_FACET), mapOf(ITEMS_STATUS_NAME_FACET, composeItemStatusFacet())),

      arguments("id=*", array(ITEMS_DISCOVERY_SUPPRESS_FACET), mapOf(
        ITEMS_DISCOVERY_SUPPRESS_FACET, composeItemDiscoverySuppressFacet())),

      arguments("id=*", array(ITEMS_STATISTICAL_CODE_IDS_FACET), mapOf(
        ITEMS_STATISTICAL_CODE_IDS_FACET, composeItemStatisticalCodeFacet())),

      arguments("id=*", array(ITEM_TAGS_FACET), mapOf(ITEM_TAGS_FACET, composeItemTagFacet())),

      arguments("id=*", array(HOLDINGS_PERMANENT_LOCATION_ID_FACET), mapOf(
        HOLDINGS_PERMANENT_LOCATION_ID_FACET, composeHoldingsPermanentLocationFacet())),

      arguments("id=*", array(HOLDINGS_DISCOVERY_SUPPRESS_FACET), mapOf(
        HOLDINGS_DISCOVERY_SUPPRESS_FACET, composeHoldingsDiscoverySuppressFacet())),

      arguments("id=*", array(HOLDINGS_TAGS_FACET), mapOf(
        HOLDINGS_TAGS_FACET, composeHoldingsTagFacet())),

      arguments("id=*", array(HOLDINGS_TYPE_ID_FACET), mapOf(
        HOLDINGS_TYPE_ID_FACET, composeHoldingsTypeFacet())),

      arguments("id=*", array(STATISTICAL_CODES_FACET), mapOf(
        STATISTICAL_CODES_FACET, composeStatisticatlCodeFacet())),

      arguments("id=*", array(STATISTICAL_CODE_IDS_FACET), mapOf(
        STATISTICAL_CODE_IDS_FACET, composeStatisticalCodeIdFacet())),

      arguments("id=*", array(HOLDINGS_STATISTICAL_CODE_IDS_FACET), mapOf(
        HOLDINGS_STATISTICAL_CODE_IDS_FACET, composeHoldingsStatisticalCodeIdFacet())),

      arguments("id=*", array(HOLDINGS_SOURCE_ID_FACET), mapOf(HOLDINGS_SOURCE_ID_FACET, composeHoldingsSourceFacet()))
    );
  }

  private static Facet composeHoldingsSourceFacet() {
    return facet(facetItem("778cd9ab-1c63-461e-9186-cea096aa9e4c", 61));
  }

  private static Facet composeHoldingsStatisticalCodeIdFacet() {
    return facet(
      facetItem("b5968c9e-cddc-4576-99e3-8e60aed8b0dd", 8),
      facetItem("6899291f-0d18-4f8d-a269-42706a5d0e27", 7),
      facetItem("9d8abbe2-1a94-4866-8731-4d12ac09f7a8", 7),
      facetItem("c4073462-6144-4b69-a543-dd131e241799", 5),
      facetItem("d9acad2f-d9ac-4b48-9097-e6ab85000001", 5),
      facetItem("b6b46869-f3c1-4370-b603-29774a1e42b1", 3),
      facetItem("e10796e0-a594-47b7-b748-3a81b69b3d9b", 2));
  }

  private static Facet composeStatisticalCodeIdFacet() {
    return facet(
      facetItem("c4073462-6144-4b69-a543-dd131e241799", 13),
      facetItem("d9acad2f-d9ac-4b48-9097-e6ab85000001", 13),
      facetItem("9d8abbe2-1a94-4866-8731-4d12ac09f7a8", 12),
      facetItem("b5968c9e-cddc-4576-99e3-8e60aed8b0dd", 8),
      facetItem("6899291f-0d18-4f8d-a269-42706a5d0e27", 7),
      facetItem("e10796e0-a594-47b7-b748-3a81b69b3d9b", 7),
      facetItem("f47b773a-bd5f-4246-ac1e-fa4adcd0dcdf", 7),
      facetItem("b6b46869-f3c1-4370-b603-29774a1e42b1", 4));
  }

  private static Facet composeStatisticatlCodeFacet() {
    return facet(
      facetItem("9d8abbe2-1a94-4866-8731-4d12ac09f7a8", 26),
      facetItem("c4073462-6144-4b69-a543-dd131e241799", 25),
      facetItem("d9acad2f-d9ac-4b48-9097-e6ab85000001", 22),
      facetItem("6899291f-0d18-4f8d-a269-42706a5d0e27", 21),
      facetItem("b5968c9e-cddc-4576-99e3-8e60aed8b0dd", 19),
      facetItem("e10796e0-a594-47b7-b748-3a81b69b3d9b", 14),
      facetItem("b6b46869-f3c1-4370-b603-29774a1e42b1", 13),
      facetItem("f47b773a-bd5f-4246-ac1e-fa4adcd0dcdf", 11));
  }

  private static Facet composeHoldingsTagFacet() {
    return facet(
      facetItem("recommended", 8),
      facetItem("ebook-available", 7),
      facetItem("reserve", 4),
      facetItem("preservation-needed", 3),
      facetItem("review-needed", 3),
      facetItem("special-order", 3),
      facetItem("new-addition", 2),
      facetItem("withdrawn-review", 2),
      facetItem("high-demand", 1),
      facetItem("holdings-tag", 1));
  }

  private static Facet composeItemTagFacet() {
    return facet(
      facetItem("new-addition", 12),
      facetItem("high-demand", 9),
      facetItem("review-needed", 9),
      facetItem("reserve", 8),
      facetItem("withdrawn-review", 8),
      facetItem("digitization-candidate", 6),
      facetItem("special-order", 6),
      facetItem("ebook-available", 5),
      facetItem("preservation-needed", 4),
      facetItem("recommended", 2),
      facetItem("item-tag", 1));
  }

  private static Facet composeItemStatisticalCodeFacet() {
    return facet(
      facetItem("6899291f-0d18-4f8d-a269-42706a5d0e27", 10),
      facetItem("c4073462-6144-4b69-a543-dd131e241799", 8),
      facetItem("9d8abbe2-1a94-4866-8731-4d12ac09f7a8", 7),
      facetItem("b6b46869-f3c1-4370-b603-29774a1e42b1", 6),
      facetItem("d9acad2f-d9ac-4b48-9097-e6ab85000001", 6),
      facetItem("e10796e0-a594-47b7-b748-3a81b69b3d9b", 5),
      facetItem("f47b773a-bd5f-4246-ac1e-fa4adcd0dcdf", 4),
      facetItem("b5968c9e-cddc-4576-99e3-8e60aed8b0dd", 3));
  }

  private static Facet composeItemDiscoverySuppressFacet() {
    return facet(facetItem("true", 2), facetItem("false", 60));
  }

  private static Facet composeSourceFacet() {
    return facet(facetItem("FOLIO", 93), facetItem("MARC", 3));
  }

  private static Facet composeSharedFacet() {
    return facet(facetItem("false", 96));
  }

  private static Facet composeTenantFacet() {
    return facet(facetItem(TENANT_ID, 96));
  }

  private static Facet composeHoldingsDiscoverySuppressFacet() {
    return facet(facetItem("false", 54), facetItem("true", 8));
  }

  private static Facet composeHoldingsPermanentLocationFacet() {
    return facet(
      facetItem(PERMANENT_LOCATIONS[3], 18),
      facetItem(PERMANENT_LOCATIONS[4], 7),
      facetItem(PERMANENT_LOCATIONS[5], 7),
      facetItem(PERMANENT_LOCATIONS[6], 6),
      facetItem(PERMANENT_LOCATIONS[7], 5),
      facetItem(PERMANENT_LOCATIONS[8], 5),
      facetItem(PERMANENT_LOCATIONS[2], 5),
      facetItem(PERMANENT_LOCATIONS[9], 4),
      facetItem(PERMANENT_LOCATIONS[1], 3),
      facetItem(PERMANENT_LOCATIONS[10], 2),
      facetItem(PERMANENT_LOCATIONS[0], 2));
  }

  private static Facet composeHoldingsTypeFacet() {
    return facet(
      facetItem(HOLDINGS_TYPES[2], 22),
      facetItem(HOLDINGS_TYPES[3], 17),
      facetItem(HOLDINGS_TYPES[1], 10),
      facetItem(HOLDINGS_TYPES[4], 6),
      facetItem(HOLDINGS_TYPES[5], 4),
      facetItem(HOLDINGS_TYPES[6], 4),
      facetItem(HOLDINGS_TYPES[0], 2));
  }

  private static Facet composeItemMaterialTypeFacet() {
    return facet(
      facetItem(MATERIAL_TYPES[2], 40),
      facetItem(MATERIAL_TYPES[3], 21),
      facetItem(MATERIAL_TYPES[4], 15),
      facetItem(MATERIAL_TYPES[5], 9),
      facetItem(MATERIAL_TYPES[6], 9),
      facetItem(MATERIAL_TYPES[7], 7),
      facetItem(MATERIAL_TYPES[8], 5),
      facetItem(MATERIAL_TYPES[1], 3),
      facetItem(MATERIAL_TYPES[0], 2));
  }

  private static Facet composeItemStatusFacet() {
    return facet(
      facetItem("Available", 40), facetItem("Checked out", 22), facetItem("Missing", 14),
      facetItem("In transit", 7), facetItem("On order", 7), facetItem("Restricted", 4),
      facetItem("Awaiting pickup", 3), facetItem("In process", 3),
      facetItem("Declared lost", 2), facetItem("Unavailable", 2), facetItem("Unknown", 2),
      facetItem("Aged to lost", 1), facetItem("Awaiting delivery", 1),
      facetItem("In progress", 1), facetItem("Withdrawn", 1));
  }

  private static Facet composeEffectiveLocationFacet() {
    return facet(
      facetItem(LOCATIONS[2], 32),
      facetItem(LOCATIONS[3], 30),
      facetItem(LOCATIONS[4], 22),
      facetItem(LOCATIONS[5], 6),
      facetItem(LOCATIONS[0], 4),
      facetItem(LOCATIONS[1], 3),
      facetItem(LOCATIONS[6], 2),
      facetItem(LOCATIONS[7], 2),
      facetItem(LOCATIONS[8], 2),
      facetItem(LOCATIONS[9], 1),
      facetItem(LOCATIONS[10], 1),
      facetItem(LOCATIONS[11], 1));
  }

  private static Facet composeInstanceFormatsFacet() {
    return facet(
      facetItem(FORMATS[3], 31),
      facetItem(FORMATS[2], 26),
      facetItem(FORMATS[1], 22),
      facetItem(FORMATS[0], 20));
  }

  private static Facet composeDiscoverySuppressFacet() {
    return facet(facetItem("false", 81), facetItem("true", 15));
  }

  private static Facet composeStaffSuppressFacet() {
    return facet(facetItem("false", 93), facetItem("true", 3));
  }

  private static Facet composeLanguagesFacet() {
    return facet(facetItem("eng", 94), facetItem("fra", 2), facetItem("ita", 2),
      facetItem("ger", 1), facetItem("rus", 1), facetItem("ukr", 1));
  }

  private static Facet composeInstanceTagsFacet() {
    return facet(
      facetItem("sort-titles", 13), facetItem("ebook-available", 11), facetItem("high-demand", 11),
      facetItem("reserve", 9), facetItem("review-needed", 9), facetItem("special-order", 9),
      facetItem("digitization-candidate", 8), facetItem("new-addition", 8), facetItem("recommended", 8),
      facetItem("withdrawn-review", 7), facetItem("preservation-needed", 6), facetItem("sort-instance", 5),
      facetItem("cooking", 2), facetItem("future", 2), facetItem("science", 2),
      facetItem("book", 1), facetItem("electronic", 1),
      facetItem("electronic book", 1), facetItem("text", 1));
  }

  private static Facet composeInstanceTypeFacet() {
    return facet(
      facetItem(TYPES[2], 56),
      facetItem(TYPES[3], 10),
      facetItem(TYPES[4], 8),
      facetItem(TYPES[5], 6),
      facetItem(TYPES[6], 5),
      facetItem(TYPES[1], 4),
      facetItem(TYPES[0], 2),
      facetItem(TYPES[7], 2),
      facetItem(TYPES[8], 2),
      facetItem(TYPES[9], 1));
  }

  private static Facet composeStatusFacet() {
    return facet(
      facetItem(STATUSES[2], 36),
      facetItem(STATUSES[0], 31),
      facetItem(STATUSES[1], 29));
  }
}
