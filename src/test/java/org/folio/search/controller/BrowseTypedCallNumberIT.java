package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.CallNumberType.DEWEY;
import static org.folio.search.model.types.CallNumberType.LC;
import static org.folio.search.model.types.CallNumberType.NLM;
import static org.folio.search.model.types.CallNumberType.OTHER;
import static org.folio.search.model.types.CallNumberType.SUDOC;
import static org.folio.search.support.base.ApiEndpoints.instanceCallNumberBrowsePath;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.cnBrowseItem;
import static org.folio.search.utils.TestUtils.getShelfKeyFromCallNumber;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.CallNumberBrowseResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.test.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class BrowseTypedCallNumberIT extends BaseIntegrationTest {

  private static final String LOCAL_TYPE_1 = "6fd29f52-5c9c-44d0-b529-e9c5eb3a0aba";
  private static final String LOCAL_TYPE_2 = "654d7565-b277-4dfa-8b7d-fbf306d9d0cd";
  private static final String FOLIO_TYPE = "6e4d7565-b277-4dfa-8b7d-fbf306d9d0cd";
  private static final Instance[] INSTANCES = instances();
  private static final Map<String, Instance> INSTANCE_MAP =
    Arrays.stream(INSTANCES).collect(toMap(Instance::getTitle, identity()));

  @BeforeAll
  static void prepare() {
    setUpTenant(INSTANCES);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void browseByCallNumberLc_browsingAroundWhenPrecedingRecordsCountIsSpecified() {
    var request = get(instanceCallNumberBrowsePath())
      .param("query",
        prepareQuery("typedCallNumber < {value} or typedCallNumber >= {value}", "\"DA 3890 A2 B76 42002\""))
      .param("callNumberType", "lc")
      .param("limit", "5")
      .param("expandAll", "true")
      .param("precedingRecordsCount", "4");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(new CallNumberBrowseResult()
      .totalRecords(23).prev("DA 43880 O6 M15").next("DA 43890 A2 B76 542002").items(List.of(
        cnBrowseItem(instance("instance #05"), "DA 3880 O6 M15"),
        cnBrowseItem(instance("instance #13"), "DA 3880 O6 M81"),
        cnBrowseItem(instance("instance #02"), "DA 3880 O6 M96"),
        cnBrowseItem(instance("instance #14"), "DA 3890 A1 I72 41885"),
        cnBrowseItem(instance("instance #22"), "DA 3890 A2 B76 42002", true)
      )));
  }

  @Test
  void browseByCallNumberDewey_browsingAroundWhenPrecedingRecordsCountIsSpecified() {
    var request = get(instanceCallNumberBrowsePath())
      .param("query", prepareQuery("typedCallNumber < {value} or typedCallNumber >= {value}", "\"1CE 16 B6724 41993\""))
      .param("callNumberType", "dewey")
      .param("limit", "5")
      .param("expandAll", "true")
      .param("precedingRecordsCount", "4");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(new CallNumberBrowseResult()
      .totalRecords(11).prev(null).next("11 CE 216 B 46724 541993").items(List.of(
        cnBrowseItem(instance("instance #44"), "1CE 16 B6713 X 41993"),
        cnBrowseItem(DEWEY, "1CE 16 B6724 41993", 2, true)
      )));
  }

  @Test
  void browseByCallNumberDewey_invalidDeweyFormat() {
    var request = get(instanceCallNumberBrowsePath())
      .param("query", prepareQuery("typedCallNumber < {value} or typedCallNumber >= {value}", "\"A 123.4\""))
      .param("callNumberType", "dewey")
      .param("limit", "3")
      .param("expandAll", "true")
      .param("precedingRecordsCount", "1");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(new CallNumberBrowseResult()
      .totalRecords(8).prev("11 CE 3210 K 3297 541858").next(null).items(List.of(
        cnBrowseItem(instance("instance #38"), "1CE 210 K297 41858"),
        cnBrowseItem(instance("instance #48"), "A 123.4", true),
        cnBrowseItem(instance("instance #49"), "A 123.5")
      )));
  }

  @Test
  void browseByShelfKeyDewey_backward() {
    var request = get(instanceCallNumberBrowsePath())
      .param("query", prepareQuery("itemEffectiveShelvingOrder < {value}", "\"A 3123.4\""))
      .param("callNumberType", "dewey")
      .param("limit", "5")
      .param("expandAll", "true");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(new CallNumberBrowseResult()
      .totalRecords(6).prev(null).next("11 CE 3210 K 3297 541858").items(List.of(
        cnBrowseItem(instance("instance #44"), "1CE 16 B6713 X 41993"),
        cnBrowseItem(DEWEY, "1CE 16 B6724 41993", 2, null),
        cnBrowseItem(instance("instance #04"), "1CE 16 D86 X 41998"),
        cnBrowseItem(instance("instance #38"), "1CE 210 K297 41858")
      )));
  }

  @Test
  void browseByCallNumberNlm_browsingAroundWhenPrecedingRecordsCountIsSpecified() {
    var request = get(instanceCallNumberBrowsePath())
      .param("query",
        prepareQuery("typedCallNumber < {value} or typedCallNumber >= {value}", "\"AC 11 E8 NO 14 P S1487\""))
      .param("callNumberType", "nlm")
      .param("limit", "5")
      .param("expandAll", "true")
      .param("precedingRecordsCount", "4");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(new CallNumberBrowseResult()
      .totalRecords(4).prev(null).next(null).items(List.of(
        cnBrowseItem(instance("instance #25"), "AC 11 A4 VOL 235"),
        cnBrowseItem(instance("instance #08"), "AC 11 A67 X 42000"),
        cnBrowseItem(instance("instance #18"), "AC 11 E8 NO 14 P S1487", true)
      )));
  }

  @Test
  void browseByCallNumberSudoc_browsingAroundWhenPrecedingRecordsCountIsSpecified() {
    var request = get(instanceCallNumberBrowsePath())
      .param("query", prepareQuery("typedCallNumber < {value} or typedCallNumber >= {value}", "\"P1.44034 B38 41993\""))
      .param("callNumberType", "sudoc")
      .param("limit", "5")
      .param("expandAll", "true")
      .param("precedingRecordsCount", "4");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(new CallNumberBrowseResult()
      .totalRecords(5).prev(null).next(null).items(List.of(
        cnBrowseItem(instance("instance #12"), "D1.211 N52 VOL 14"),
        cnBrowseItem(instance("instance #10"), "D1.3201 B34 41972"),
        cnBrowseItem(instance("instance #17"), "G1.16 A63 41581"),
        cnBrowseItem(instance("instance #16"), "P1.44034 B38 41993", true)
      )));
  }

  @Test
  void browseByCallNumberLocal_browsingAroundWhenPrecedingRecordsCountIsSpecified() {
    var request = get(instanceCallNumberBrowsePath())
      .param("query",
        prepareQuery("typedCallNumber < {value} or typedCallNumber >= {value}", "\"F  PR1866.S63 V.1 C.1\""))
      .param("callNumberType", "local")
      .param("limit", "5")
      .param("expandAll", "true")
      .param("precedingRecordsCount", "4");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);

    var multipleItemsInstance = instance("instance #27");
    assertThat(actual).isEqualTo(new CallNumberBrowseResult()
      .totalRecords(12).prev("DB 11 A66 SUPPL NO 11").next("F  PR1866.S63 V.1 C.1").items(List.of(
        cnBrowseItem(instance("instance #23"), "DB 11 A66 SUPPL NO 11"),
        cnBrowseItem(instance("instance #35"), "E 12.11 I12 288 D"),
        cnBrowseItem(instance("instance #33"), "E 12.11 I2 298"),
        cnBrowseItem(instance(multipleItemsInstance, multipleItemsInstance.getItems().get(0)), "E 211 A506"),
        cnBrowseItem(instance("instance #46"), "F  PR1866.S63 V.1 C.1", true)
      )));
  }

  @Test
  void browseByCallNumberLocal_browsingForwardIncluding() {
    var request = get(instanceCallNumberBrowsePath())
      .param("query", prepareQuery("itemEffectiveShelvingOrder >= {value}", "\"F  PR1866.S63 V.1 C.1\""))
      .param("callNumberType", "local")
      .param("limit", "5")
      .param("expandAll", "true");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(new CallNumberBrowseResult()
      .totalRecords(5).prev("F  PR1866.S63 V.1 C.1").next(null).items(List.of(
        cnBrowseItem(instance("instance #46"), "F  PR1866.S63 V.1 C.1"),
        cnBrowseItem(instance("instance #37"), "FC 17 B89"),
        cnBrowseItem(instance("instance #30"), "GA 16 G32 41557 V1"),
        cnBrowseItem(instance("instance #26"), "PR 17 I55 42006"),
        cnBrowseItem(instance("instance #40"), "PR 213 E5 41999")
      )));
  }

  @Test
  void getFacets_positive_withFiltrationByCallNumberType() throws Exception {
    var requestBuilder = get("/search/instances/facets")
      .queryParam("query", "callNumberType=\"dewey\"")
      .queryParam("facet", "item.effectiveLocationId:5")
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.facets.['items.effectiveLocationId'].totalRecords", is(1)))
      .andExpect(jsonPath("$.facets.['items.effectiveLocationId'].values[0].id", is(DEWEY.getId())))
      .andExpect(jsonPath("$.facets.['items.effectiveLocationId'].values[0].totalRecords", is(7)));
  }

  private static Instance[] instances() {
    return callNumberBrowseInstanceData().stream()
      .map(BrowseTypedCallNumberIT::instance)
      .toArray(Instance[]::new);
  }

  @SuppressWarnings("unchecked")
  private static Instance instance(List<Object> data) {
    var items = ((List<List<String>>) data.get(1)).stream()
      .map(callNumber -> new Item()
        .id(randomId())
        .discoverySuppress(false)
        .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents()
          .callNumber(callNumber.get(0)).typeId(callNumber.get(1)))
        .effectiveShelvingOrder(getShelfKeyFromCallNumber(callNumber.get(0), callNumber.get(1)))
        .effectiveLocationId(callNumber.get(1)))
      .toList();

    return new Instance()
      .id(randomId())
      .title((String) data.get(0))
      .staffSuppress(false)
      .discoverySuppress(false)
      .isBoundWith(false)
      .shared(false)
      .tenantId(TENANT_ID)
      .items(items)
      .holdings(emptyList());
  }

  private static Instance instance(String title) {
    return INSTANCE_MAP.get(title);
  }

  private static Instance instance(Instance instance, Item item) {
    return new Instance()
      .id(instance.getId())
      .title(instance.getTitle())
      .staffSuppress(instance.getStaffSuppress())
      .discoverySuppress(instance.getDiscoverySuppress())
      .isBoundWith(instance.getIsBoundWith())
      .shared(instance.getShared())
      .tenantId(instance.getTenantId())
      .items(singletonList(item))
      .holdings(instance.getHoldings());
  }

  private static List<List<Object>> callNumberBrowseInstanceData() {
    return List.of(
      List.of("instance #01", List.of(List.of("DA 3880 O6 J72", LC.getId()))),
      List.of("instance #02", List.of(List.of("DA 3880 O6 M96", LC.getId()))),
      List.of("instance #03", List.of(List.of("DA 3880 O6 L5 41955", LC.getId()))),
      List.of("instance #04", List.of(List.of("1CE 16 D86 X 41998", DEWEY.getId()))),
      List.of("instance #05", List.of(List.of("DA 3880 O6 M15", LC.getId()))),
      List.of("instance #06", List.of(List.of("DA 3880 O6 L6 V1", LC.getId()))),
      List.of("instance #07", List.of(List.of("DA 3870 H47 41975", LC.getId()))),
      List.of("instance #08", List.of(List.of("AC 11 A67 X 42000", NLM.getId()))),
      List.of("instance #09", List.of(List.of("DA 3700 C95 NO 18", LC.getId()))),
      List.of("instance #10", List.of(List.of("D1.3201 B34 41972", SUDOC.getId()))),
      List.of("instance #11", List.of(List.of("DA 3880 K56 M27 41984", LC.getId()))),
      List.of("instance #12", List.of(List.of("D1.211 N52 VOL 14", SUDOC.getId()))),
      List.of("instance #13", List.of(List.of("DA 3880 O6 M81", LC.getId()))),
      List.of("instance #14", List.of(List.of("DA 3890 A1 I72 41885", LC.getId()))),
      List.of("instance #15", List.of(List.of("DA 3880 O6 L76", LC.getId()))),
      List.of("instance #16", List.of(List.of("P1.44034 B38 41993", SUDOC.getId()))),
      List.of("instance #17", List.of(List.of("G1.16 A63 41581", SUDOC.getId()))),
      List.of("instance #18", List.of(List.of("AC 11 E8 NO 14 P S1487", NLM.getId()))),
      List.of("instance #19", List.of(List.of("DA 3890 A2 F57 42011", LC.getId()))),
      List.of("instance #20", List.of(List.of("DA 3880 O6 L75", LC.getId()))),
      List.of("instance #21", List.of(List.of("FC 17 B89", OTHER.getId()))),
      List.of("instance #22", List.of(List.of("DA 3890 A2 B76 42002", LC.getId()))),
      List.of("instance #23", List.of(List.of("DB 11 A66 SUPPL NO 11", LOCAL_TYPE_1))),
      List.of("instance #24", List.of(List.of("DA 3900 C39 NO 11", LC.getId()))),
      List.of("instance #25", List.of(List.of("AC 11 A4 VOL 235", NLM.getId()))),
      List.of("instance #26", List.of(List.of("PR 17 I55 42006", LOCAL_TYPE_1))),
      List.of("instance #27", List.of(List.of("E 211 A506", LOCAL_TYPE_1), List.of("E 311 A506", FOLIO_TYPE))),
      List.of("instance #28", List.of(List.of("DB 11 A31 BD 3124", LOCAL_TYPE_2))),
      List.of("instance #29", List.of(List.of("DA 3880 O6 D5", LC.getId()))),
      List.of("instance #30", List.of(List.of("GA 16 G32 41557 V1", LOCAL_TYPE_2))),
      List.of("instance #31", List.of(List.of("AB 14 C72 NO 220", LOCAL_TYPE_1))),
      List.of("instance #32", List.of(List.of("DA 3880 O5 C3 V1", LC.getId()))),
      List.of("instance #33", List.of(List.of("E 12.11 I2 298", LOCAL_TYPE_1))),
      List.of("instance #34", List.of(List.of("DA 3900 C89 V1", LC.getId()))),
      List.of("instance #35", List.of(List.of("E 12.11 I12 288 D", LOCAL_TYPE_2))),
      List.of("instance #36", List.of(List.of("DA 3700 B91 L79", LC.getId()))),
      List.of("instance #37", List.of(List.of("FC 17 B89", LOCAL_TYPE_2))),
      List.of("instance #38", List.of(List.of("1CE 210 K297 41858", DEWEY.getId()))),
      List.of("instance #39", List.of(List.of("GA 16 D64 41548A", OTHER.getId()))),
      List.of("instance #40", List.of(List.of("PR 213 E5 41999", LOCAL_TYPE_2))),
      List.of("instance #41", List.of(List.of("DA 3870 B55 41868", LC.getId()))),
      List.of("instance #42", List.of(List.of("FA 46252 3977 12 237", OTHER.getId()))),
      List.of("instance #43", List.of(List.of("FA 42010 3546 256", OTHER.getId()))),
      List.of("instance #44", List.of(List.of("1CE 16 B6713 X 41993", DEWEY.getId()))),
      List.of("instance #45", List.of(List.of("1CE 16 B6724 41993", DEWEY.getId()))),
      List.of("instance #46", List.of(List.of("F  PR1866.S63 V.1 C.1", LOCAL_TYPE_1))),
      List.of("instance #47", List.of(List.of("1CE 16 B6724 41993", DEWEY.getId()))),
      List.of("instance #48", List.of(List.of("A 123.4", DEWEY.getId()))),
      List.of("instance #49", List.of(List.of("A 123.5", DEWEY.getId())))
    );
  }
}
