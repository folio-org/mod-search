package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.support.base.ApiEndpoints.instanceCallNumberBrowsePath;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.cnBrowseItem;
import static org.folio.search.utils.TestUtils.getShelfKeyFromCallNumber;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.folio.search.utils.TestUtils.randomId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.CallNumberBrowseResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.test.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class BrowseCallNumberPrecedingIT extends BaseIntegrationTest {

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
  void browseByCallNumber_browsingAroundPrecedingRecordsWithSame10FirstSymbols() {
    var request = get(instanceCallNumberBrowsePath())
      .param("query", prepareQuery("callNumber < {value} or callNumber >= {value}", "\"E 3184 S75 1234\""))
      .param("limit", "15")
      .param("expandAll", "true")
      .param("highlightMatch", "true")
      .param("precedingRecordsCount", "5");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(new CallNumberBrowseResult()
      .totalRecords(60).prev("E 43184 S74 45673").next("E 43184 S75 41243").items(List.of(
        cnBrowseItem(instance("instance #29"), "E 3184 S74 5673"),
        cnBrowseItem(instance("instance #30"), "E 3184 S74 5674"),
        cnBrowseItem(instance("instance #02"), "E 3184 S75 1231"),
        cnBrowseItem(instance("instance #01"), "E 3184 S75 1232"),
        cnBrowseItem(instance("instance #03"), "E 3184 S75 1233"),
        cnBrowseItem(instance("instance #04"), "E 3184 S75 1234", true),
        cnBrowseItem(instance("instance #05"), "E 3184 S75 1235"),
        cnBrowseItem(instance("instance #06"), "E 3184 S75 1236"),
        cnBrowseItem(instance("instance #07"), "E 3184 S75 1237"),
        cnBrowseItem(instance("instance #08"), "E 3184 S75 1238"),
        cnBrowseItem(instance("instance #09"), "E 3184 S75 1239"),
        cnBrowseItem(instance("instance #10"), "E 3184 S75 1240"),
        cnBrowseItem(instance("instance #11"), "E 3184 S75 1241"),
        cnBrowseItem(instance("instance #12"), "E 3184 S75 1242"),
        cnBrowseItem(instance("instance #13"), "E 3184 S75 1243")
      )));
  }

  private static Instance[] instances() {
    return callNumberBrowseInstanceData().stream()
      .map(BrowseCallNumberPrecedingIT::instance)
      .toArray(Instance[]::new);
  }

  @SuppressWarnings("unchecked")
  private static Instance instance(List<Object> data) {
    var items = ((List<String>) data.get(1)).stream()
      .map(callNumber -> new Item()
        .id(randomId())
        .discoverySuppress(false)
        .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().callNumber(callNumber))
        .effectiveShelvingOrder(getShelfKeyFromCallNumber(callNumber)))
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

  private static List<List<Object>> callNumberBrowseInstanceData() {
    return List.of(
      List.of("instance #27", List.of("E 3184 S74 5671")),
      List.of("instance #28", List.of("E 3184 S74 5672")),
      List.of("instance #29", List.of("E 3184 S74 5673")),
      List.of("instance #30", List.of("E 3184 S74 5674")),
      List.of("instance #31", List.of("A 3184 S74 ")),
      List.of("instance #32", List.of("A 3184 S75 1235")),
      List.of("instance #02", List.of("E 3184 S75 1231")),
      List.of("instance #01", List.of("E 3184 S75 1232")),
      List.of("instance #03", List.of("E 3184 S75 1233")),
      List.of("instance #04", List.of("E 3184 S75 1234")),
      List.of("instance #05", List.of("E 3184 S75 1235")),
      List.of("instance #06", List.of("E 3184 S75 1236")),
      List.of("instance #07", List.of("E 3184 S75 1237")),
      List.of("instance #08", List.of("E 3184 S75 1238")),
      List.of("instance #09", List.of("E 3184 S75 1239")),
      List.of("instance #10", List.of("E 3184 S75 1240")),
      List.of("instance #11", List.of("E 3184 S75 1241")),
      List.of("instance #12", List.of("E 3184 S75 1242")),
      List.of("instance #13", List.of("E 3184 S75 1243")),
      List.of("instance #14", List.of("E 3184 S75 1244")),
      List.of("instance #15", List.of("E 3184 S75 1245")),
      List.of("instance #16", List.of("E 3184 S75 1246")),
      List.of("instance #17", List.of("E 3184 S75 1247")),
      List.of("instance #18", List.of("E 3184 S75 1248")),
      List.of("instance #19", List.of("E 3184 S75 1249")),
      List.of("instance #20", List.of("E 3184 S75 1250")),
      List.of("instance #21", List.of("E 3184 S75 1251")),
      List.of("instance #22", List.of("E 3184 S75 1252")),
      List.of("instance #23", List.of("E 3184 S75 1253")),
      List.of("instance #24", List.of("E 3184 S75 1254")),
      List.of("instance #25", List.of("E 3184 S75 1255"))
    );
  }
}
