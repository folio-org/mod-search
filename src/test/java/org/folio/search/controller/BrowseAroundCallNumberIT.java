package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.support.base.ApiEndpoints.instanceCallNumberBrowsePath;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.cnBrowseItemWithNoType;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.shelfKeyForNotTypedCallNumberFunction;
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
class BrowseAroundCallNumberIT extends BaseIntegrationTest {

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
      .param("limit", "19")
      .param("expandAll", "true")
      .param("highlightMatch", "true")
      .param("precedingRecordsCount", "9");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(new CallNumberBrowseResult()
      .totalRecords(59).prev("C23 987").next("E 3184 S75 1243").items(List.of(
        cnBrowseItemWithNoType(instance("instance #34"), "C23 987"),
        cnBrowseItemWithNoType(instance("instance #33"), "D15.H63 A3 2002"),
        cnBrowseItemWithNoType(instance("instance #27"), "E 3184 S74 5671"),
        cnBrowseItemWithNoType(instance("instance #28"), "E 3184 S74 5672"),
        cnBrowseItemWithNoType(instance("instance #29"), "E 3184 S74 5673"),
        cnBrowseItemWithNoType(instance("instance #30"), "E 3184 S74 5674"),
        cnBrowseItemWithNoType(instance("instance #02"), "E 3184 S75 1231"),
        cnBrowseItemWithNoType(instance("instance #01"), "E 3184 S75 1232"),
        cnBrowseItemWithNoType(instance("instance #03"), "E 3184 S75 1233"),
        cnBrowseItemWithNoType(instance("instance #04"), "E 3184 S75 1234", true),
        cnBrowseItemWithNoType(instance("instance #05"), "E 3184 S75 1235"),
        cnBrowseItemWithNoType(instance("instance #06"), "E 3184 S75 1236"),
        cnBrowseItemWithNoType(instance("instance #07"), "E 3184 S75 1237"),
        cnBrowseItemWithNoType(instance("instance #08"), "E 3184 S75 1238"),
        cnBrowseItemWithNoType(instance("instance #09"), "E 3184 S75 1239"),
        cnBrowseItemWithNoType(instance("instance #10"), "E 3184 S75 1240"),
        cnBrowseItemWithNoType(instance("instance #11"), "E 3184 S75 1241"),
        cnBrowseItemWithNoType(instance("instance #12"), "E 3184 S75 1242"),
        cnBrowseItemWithNoType(instance("instance #13"), "E 3184 S75 1243")
      )));
  }

  @Test
  void browseByCallNumber_browsingAroundSucceedingRecordsWithSame10FirstSymbols() {
    var request = get(instanceCallNumberBrowsePath())
      .param("query", prepareQuery("callNumber < {value} or callNumber >= {value}", "\"E 3184 S75 1234\""))
      .param("limit", "24")
      .param("expandAll", "true")
      .param("highlightMatch", "true")
      .param("precedingRecordsCount", "1");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(new CallNumberBrowseResult()
      .totalRecords(59).prev("E 3184 S75 1233").next("G75 1255").items(List.of(
        cnBrowseItemWithNoType(instance("instance #03"), "E 3184 S75 1233"),
        cnBrowseItemWithNoType(instance("instance #04"), "E 3184 S75 1234", true),
        cnBrowseItemWithNoType(instance("instance #05"), "E 3184 S75 1235"),
        cnBrowseItemWithNoType(instance("instance #06"), "E 3184 S75 1236"),
        cnBrowseItemWithNoType(instance("instance #07"), "E 3184 S75 1237"),
        cnBrowseItemWithNoType(instance("instance #08"), "E 3184 S75 1238"),
        cnBrowseItemWithNoType(instance("instance #09"), "E 3184 S75 1239"),
        cnBrowseItemWithNoType(instance("instance #10"), "E 3184 S75 1240"),
        cnBrowseItemWithNoType(instance("instance #11"), "E 3184 S75 1241"),
        cnBrowseItemWithNoType(instance("instance #12"), "E 3184 S75 1242"),
        cnBrowseItemWithNoType(instance("instance #13"), "E 3184 S75 1243"),
        cnBrowseItemWithNoType(instance("instance #14"), "E 3184 S75 1244"),
        cnBrowseItemWithNoType(instance("instance #15"), "E 3184 S75 1245"),
        cnBrowseItemWithNoType(instance("instance #16"), "E 3184 S75 1246"),
        cnBrowseItemWithNoType(instance("instance #18"), "E 3184 S75 1248"),
        cnBrowseItemWithNoType(instance("instance #19"), "E 3184 S75 1249"),
        cnBrowseItemWithNoType(instance("instance #20"), "E 3184 S75 1250"),
        cnBrowseItemWithNoType(instance("instance #21"), "E 3184 S75 1251"),
        cnBrowseItemWithNoType(instance("instance #22"), "E 3184 S75 1252"),
        cnBrowseItemWithNoType(instance("instance #23"), "E 3184 S75 1253"),
        cnBrowseItemWithNoType(instance("instance #24"), "E 3184 S75 1254"),
        cnBrowseItemWithNoType(instance("instance #17"), "E 3184 S76 1247"),
        cnBrowseItemWithNoType(instance("instance #38"), "FA 42010 3546 256"),
        cnBrowseItemWithNoType(instance("instance #35"), "G75 1255")
      )));
  }

  private static Instance[] instances() {
    return callNumberBrowseInstanceData().stream()
      .map(BrowseAroundCallNumberIT::instance)
      .toArray(Instance[]::new);
  }

  @SuppressWarnings("unchecked")
  private static Instance instance(List<Object> data) {
    var items = ((List<String>) data.get(1)).stream()
      .map(callNumber -> new Item()
        .id(randomId())
        .discoverySuppress(false)
        .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().callNumber(callNumber))
        .effectiveShelvingOrder(shelfKeyForNotTypedCallNumberFunction().apply(callNumber)))
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

  private Instance instance(String title) {
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
      List.of("instance #33", List.of("D15.H63 A3 2002")),
      List.of("instance #34", List.of("C23 987")),
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
      List.of("instance #17", List.of("E 3184 S76 1247")),
      List.of("instance #18", List.of("E 3184 S75 1248")),
      List.of("instance #19", List.of("E 3184 S75 1249")),
      List.of("instance #20", List.of("E 3184 S75 1250")),
      List.of("instance #21", List.of("E 3184 S75 1251")),
      List.of("instance #22", List.of("E 3184 S75 1252")),
      List.of("instance #23", List.of("E 3184 S75 1253")),
      List.of("instance #24", List.of("E 3184 S75 1254")),
      List.of("instance #35", List.of("G75 1255")),
      List.of("instance #36", List.of("PR 213 E5 41999")),
      List.of("instance #37", List.of("GA 16 D64 41548A")),
      List.of("instance #38", List.of("FA 42010 3546 256"))
    );
  }
}
