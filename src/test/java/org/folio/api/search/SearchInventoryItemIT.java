package org.folio.api.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.RecordType.ITEMS;
import static org.folio.search.utils.SearchUtils.ALL_RECORDS_QUERY;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.itemSearchPath;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.testdata.SharedTestDataManager.itemsCount;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for {@code GET /search/inventory/items} over the standalone item index populated from the shared dataset.
 */
public abstract class SearchInventoryItemIT extends BaseSharedTest {

  private static final String DISCOVERY_SUPPRESS_FACET = "discoverySuppress";
  private static final String EFFECTIVE_LOCATION_ID_FACET = "effectiveLocationId";
  private static final String MATERIAL_TYPE_ID_FACET = "materialTypeId";
  private static final String STATISTICAL_CODE_IDS_FACET = "statisticalCodeIds";
  private static final String STATUS_NAME_FACET = "statusName";

  private static final String ITEM_ID = "00000001-0000-4000-a000-000000000000";
  private static final String ITEM_WITH_ACCESSION_NUMBER_ID = "00000004-0000-4000-a000-000000000000";
  private static final String INSTANCE_ID = "00000008-0000-4000-8000-000000000000";

  @Test
  void searchById_positive_returnsDefaultFieldsAndInstanceTitle() throws Exception {
    doSearch(itemSearchPath(), "id==" + ITEM_ID, TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.items[0].id", is(ITEM_ID)))
      .andExpect(jsonPath("$.items[0].instanceId", is(INSTANCE_ID)))
      .andExpect(jsonPath("$.items[0].holdingsRecordId", is("00000014-0000-4000-9000-000000000000")))
      .andExpect(jsonPath("$.items[0].barcode", is("39006441908806")))
      .andExpect(jsonPath("$.items[0].statusName", is("Available")))
      .andExpect(jsonPath("$.items[0].callNumber", is("TK5105.88815 . A58 2004 FT MEADE")))
      .andExpect(jsonPath("$.items[0].callNumberPrefix", is("REF")))
      .andExpect(jsonPath("$.items[0].callNumberSuffix", is("c.2")))
      .andExpect(jsonPath("$.items[0].callNumberTypeId", is("512173a7-bd09-490e-b773-17d83f2b63fe")))
      .andExpect(jsonPath("$.items[0].effectiveLocationId", is("fcd64ce1-6995-48f0-840e-89ffa2288371")))
      .andExpect(jsonPath("$.items[0].materialTypeId", is("1a54b431-2e4f-452d-9cae-9cee66c9a892")))
      .andExpect(jsonPath("$.items[0].instanceTitle", containsString("web primer")))
      .andExpect(jsonPath("$.items[0].hrid").doesNotExist())
      .andExpect(jsonPath("$.items[0].tenantId").doesNotExist());
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @CsvSource(delimiter = '|', value = {
    "barcode==39006441908806|1",
    "hrid==it000100000015|1",
    "instanceId==" + INSTANCE_ID + "|3",
    "holdingsRecordId==00000014-0000-4000-9000-000000000000|2",
    "effectiveLocationId==fcd64ce1-6995-48f0-840e-89ffa2288371|10",
    "materialTypeId==1a54b431-2e4f-452d-9cae-9cee66c9a892|65",
    "statusName==\"Checked out\"|25",
    "discoverySuppress==true|2",
    "formerIds==81ae0f60-f2bc-450c-84c8-5a21096daed9|1",
    "accessionNumber==item_accession_number|1",
    "itemIdentifier==itemIdentifierFieldValue|1",
    "callNumber==\"P2719 .H61 2004\"|5",
    "callNumber==p2719*|5",
    "callNumberPrefix==gen|3",
    "callNumberSuffix==\"FT MEADE\"|2",
    "barcode==nonexistent|0"
  })
  void search_parameterized_byItemFields(String query, int expectedTotal) throws Exception {
    doSearch(itemSearchPath(), query, TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(expectedTotal)))
      .andExpect(jsonPath("$.items", hasSize(expectedTotal)));
  }

  @Test
  void searchAll_positive_returnsAllItems() throws Exception {
    doSearch(itemSearchPath(), ALL_RECORDS_QUERY, TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(itemsCount())));
  }

  @Test
  void search_positive_pagination() throws Exception {
    doSearch(itemSearchPath(), TENANT_ID, Map.of("query", ALL_RECORDS_QUERY, "limit", "5", "offset", "140"))
      .andExpect(jsonPath("$.totalRecords", is(itemsCount())))
      .andExpect(jsonPath("$.items", hasSize(itemsCount() - 140)));
  }

  @Test
  void search_positive_includeAddsFieldsToDefaultSet() throws Exception {
    var query = "id==" + ITEM_WITH_ACCESSION_NUMBER_ID;

    doSearch(itemSearchPath(), query, TENANT_ID)
      .andExpect(jsonPath("$.items[0].accessionNumber").doesNotExist());

    doSearch(itemSearchPath(), TENANT_ID, Map.of("query", query, "include", "accessionNumber"))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.items[0].accessionNumber", is("item_accession_number")))
      .andExpect(jsonPath("$.items[0].barcode", is("39006673198930")));
  }

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  void getFacetsForItems_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(doGet(recordFacetsPath(ITEMS, query, facets), TENANT_ID), FacetResult.class);

    assertThat(actual.getFacets()).isNotNull();
    expected.forEach((facetName, expectedFacet) -> {
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).as("Facet %s exists", facetName).isNotNull();
      assertThat(actualFacet.getValues()).as("Facet %s has expected values", facetName)
        .containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }

  @Test
  void getFacetsForItems_negative_invalidFacetName() throws Exception {
    attemptGet(recordFacetsPath(ITEMS, ALL_RECORDS_QUERY, "unknownFacet:5"), TENANT_ID)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is("Invalid facet value")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("facet")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("unknownFacet")));
  }

  private static Stream<Arguments> facetQueriesProvider() {
    var allFacets = array(STATUS_NAME_FACET, EFFECTIVE_LOCATION_ID_FACET, MATERIAL_TYPE_ID_FACET,
      STATISTICAL_CODE_IDS_FACET, DISCOVERY_SUPPRESS_FACET);
    return Stream.of(
      arguments(ALL_RECORDS_QUERY, allFacets, mapOf(
        STATUS_NAME_FACET, composeStatusNameFacet(),
        EFFECTIVE_LOCATION_ID_FACET, composeEffectiveLocationFacet(),
        MATERIAL_TYPE_ID_FACET, composeMaterialTypeFacet(),
        STATISTICAL_CODE_IDS_FACET, composeStatisticalCodeFacet(),
        DISCOVERY_SUPPRESS_FACET, composeDiscoverySuppressFacet())),
      arguments(ALL_RECORDS_QUERY, array(STATUS_NAME_FACET + ":2"), mapOf(
        STATUS_NAME_FACET, facet(facetItem("Available", 67), facetItem("Checked out", 25)))),
      arguments("statusName==\"Checked out\"", array(EFFECTIVE_LOCATION_ID_FACET), mapOf(
        EFFECTIVE_LOCATION_ID_FACET, facet(
          facetItem("65b6c2e9-8a7b-4a10-9b5d-ba1cf0313cd7", 11),
          facetItem("0d106980-1789-42ac-b355-a6c7a74ddea3", 5),
          facetItem("b777f3a4-4372-4792-a87d-8e8f177eab10", 5),
          facetItem("f1a49577-5096-4771-a8a0-d07d642241eb", 1),
          facetItem("ce23dfa1-17e8-4a1f-ad6b-34ce6ab352c2", 1),
          facetItem("53cf956f-c1df-410b-8bea-27f712cca7c0", 1),
          facetItem("fcd64ce1-6995-48f0-840e-89ffa2288371", 1)))),
      arguments("discoverySuppress==true", array(DISCOVERY_SUPPRESS_FACET), mapOf(
        DISCOVERY_SUPPRESS_FACET, facet(facetItem("true", 2), facetItem("false", 141))))
    );
  }

  private static Facet composeStatusNameFacet() {
    return facet(
      facetItem("Available", 67),
      facetItem("Checked out", 25),
      facetItem("Missing", 15),
      facetItem("In transit", 7),
      facetItem("On order", 7),
      facetItem("Restricted", 5),
      facetItem("Awaiting pickup", 3),
      facetItem("In process", 3),
      facetItem("Withdrawn", 2),
      facetItem("In progress", 2),
      facetItem("Unavailable", 2),
      facetItem("Declared lost", 2),
      facetItem("Aged to lost", 1),
      facetItem("Awaiting delivery", 1),
      facetItem("Unknown", 1));
  }

  private static Facet composeEffectiveLocationFacet() {
    return facet(
      facetItem("65b6c2e9-8a7b-4a10-9b5d-ba1cf0313cd7", 49),
      facetItem("b777f3a4-4372-4792-a87d-8e8f177eab10", 37),
      facetItem("0d106980-1789-42ac-b355-a6c7a74ddea3", 26),
      facetItem("fcd64ce1-6995-48f0-840e-89ffa2288371", 10),
      facetItem("ce23dfa1-17e8-4a1f-ad6b-34ce6ab352c2", 5),
      facetItem("f1a49577-5096-4771-a8a0-d07d642241eb", 3),
      facetItem("184aae84-a5bf-4c6a-85ba-4a7c73026cd5", 3),
      facetItem("f34d27c6-a8eb-461b-acd6-5dea81771e70", 3),
      facetItem("b241764c-1466-4e1d-a028-1a3684a5da87", 3),
      facetItem("53cf956f-c1df-410b-8bea-27f712cca7c0", 2),
      facetItem("cdd60388-0c75-4969-b3c5-2d04621ed26f", 1),
      facetItem("4fdca025-1629-4688-aeb7-9c5fe5c73549", 1));
  }

  private static Facet composeMaterialTypeFacet() {
    return facet(
      facetItem("1a54b431-2e4f-452d-9cae-9cee66c9a892", 65),
      facetItem("d9acad2f-2aac-4b48-9097-e6ab85906b25", 23),
      facetItem("615b8413-82d5-4203-aa6e-e37984cb5ac3", 18),
      facetItem("30b3e36a-d3b2-415e-98c2-47fbdf878862", 10),
      facetItem("5ee11d91-f7e8-481d-b079-65d708582ccc", 9),
      facetItem("fd6c6515-d470-4561-9c32-3e3290d4ca98", 7),
      facetItem("dd0bf600-dbd9-44ab-9ff2-e2a61a6539f1", 5),
      facetItem("3d413322-1dee-431b-bd73-b1e399063260", 4),
      facetItem("c898029e-9a02-4b61-bedb-6956cff21bc2", 2));
  }

  private static Facet composeStatisticalCodeFacet() {
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

  private static Facet composeDiscoverySuppressFacet() {
    return facet(facetItem("false", 141), facetItem("true", 2));
  }
}
