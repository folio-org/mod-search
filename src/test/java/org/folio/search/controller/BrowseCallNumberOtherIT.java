package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CN_INTERMEDIATE_VALUES;
import static org.folio.search.support.base.ApiEndpoints.instanceCallNumberBrowsePath;
import static org.folio.search.utils.TestUtils.cnBrowseItem;
import static org.folio.search.utils.TestUtils.cnBrowseResult;
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
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class BrowseCallNumberOtherIT extends BaseIntegrationTest {

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
  void browseByCallNumber_browsingAroundWithDisabledIntermediateValues() {
    var request = get(instanceCallNumberBrowsePath())
      .param("query", prepareQuery("callNumber >= {value} or callNumber < {value}", "g"))
      .param("limit", "15").param("expandAll", "true");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(cnBrowseResult(12, List.of(
      cnBrowseItem(instance("instance #04"), "3350.28"),
      cnBrowseItem(instance("instance #05"), "3362.82 292 220"),
      cnBrowseItem(instance("instance #02"), "DA 3880 O6 M96"),
      cnBrowseItem(instance("instance #09"), "F  PR1866.S63 V.1 C.1"),
      cnBrowseItem(instance("instance #11"), "F-1,452"),
      cnBrowseItem(instance("instance #10"), "FA 42010 3546 256"),
      cnBrowseItem(0, "g", true),
      cnBrowseItem(instance("instance #12"), "G  SHELF#1", "G (shelf#1)"),
      cnBrowseItem(instance("instance #03"), "PICCADILLY JZ 4 C.1", "Piccadilly Jz 4 c.1"),
      cnBrowseItem(instance("instance #01"), "PICKWIC JZ 9 C.1", "Pickwic Jz 9 c.1"),
      cnBrowseItem(instance("instance #08"), "PIRANHA 19 _C 11"),
      cnBrowseItem(instance("instance #06"), "PIROUET JAS 19035 C.1", "Pirouet JAS 19035 c.1"),
      cnBrowseItem(instance("instance #07"), "RAW 22")
    )));
  }

  @Test
  void browseByCallNumber_browsingAroundWithEnabledIntermediateValues() {
    enableFeature(BROWSE_CN_INTERMEDIATE_VALUES);

    var request = get(instanceCallNumberBrowsePath())
      .param("query", prepareQuery("callNumber >= {value} or callNumber < {value}", "g"))
      .param("limit", "15").param("expandAll", "true");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    assertThat(actual).isEqualTo(cnBrowseResult(12, List.of(
      cnBrowseItem(instance("instance #04"), "3350.28"),
      cnBrowseItem(instance("instance #05"), "3362.82 292 220"),
      cnBrowseItem(instance("instance #02"), "DA 3880 O6 M96"),
      cnBrowseItem(instance("instance #09"), "F  PR1866.S63 V.1 C.1"),
      cnBrowseItem(instance("instance #11"), "F-1,452"),
      cnBrowseItem(instance("instance #10"), "FA 42010 3546 256"),
      cnBrowseItem(0, "g", true),
      cnBrowseItem(instance("instance #12"), "G  SHELF#1", "G (shelf#1)"),
      cnBrowseItem(instance("instance #03"), "PICCADILLY JZ 4 C.1", "Piccadilly Jz 4 c.1"),
      cnBrowseItem(instance("instance #01"), "PICKWIC JZ 9 C.1", "Pickwic Jz 9 c.1"),
      cnBrowseItem(instance("instance #08"), "PIRANHA 19 _C 11"),
      cnBrowseItem(instance("instance #06"), "PIROUET JAS 19035 C.1", "Pirouet JAS 19035 c.1"),
      cnBrowseItem(instance("instance #07"), "RAW 22")
    )));

    disableFeature(BROWSE_CN_INTERMEDIATE_VALUES);
  }

  private static Instance[] instances() {
    return callNumberBrowseInstanceData().stream()
      .map(BrowseCallNumberOtherIT::instance)
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
      .items(items)
      .holdings(emptyList());
  }

  private static Instance instance(String title) {
    return INSTANCE_MAP.get(title);
  }

  private static List<List<Object>> callNumberBrowseInstanceData() {
    return List.of(
      List.of("instance #01", List.of("Pickwic Jz 9 c.1")),
      List.of("instance #02", List.of("DA 3880 O6 M96")),
      List.of("instance #03", List.of("Piccadilly Jz 4 c.1")),
      List.of("instance #04", List.of("3350.28")),
      List.of("instance #05", List.of("3362.82 292 220")),
      List.of("instance #06", List.of("Pirouet JAS 19035 c.1")),
      List.of("instance #07", List.of("RAW 22")),
      List.of("instance #08", List.of("PIRANHA 19 _C 11")),
      List.of("instance #09", List.of("F  PR1866.S63 V.1 C.1")),
      List.of("instance #10", List.of("FA 42010 3546 256")),
      List.of("instance #11", List.of("F-1,452")),
      List.of("instance #12", List.of("G (shelf#1)"))
    );
  }
}
