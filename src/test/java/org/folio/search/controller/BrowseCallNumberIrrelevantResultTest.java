package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CN_INTERMEDIATE_VALUES;
import static org.folio.search.support.base.ApiEndpoints.instanceCallNumberBrowsePath;
import static org.folio.search.utils.TestConstants.TENANT_ID;
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
import org.folio.search.utils.CallNumberUtils;
import org.folio.spring.test.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class BrowseCallNumberIrrelevantResultTest extends BaseIntegrationTest {

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
  void browseByCallNumber_browsingAroundWithEnabledIntermediateValues() {
    enableFeature(BROWSE_CN_INTERMEDIATE_VALUES);

    var request = get(instanceCallNumberBrowsePath())
      .param("callNumberType", "dewey")
      .param("query", prepareQuery("typedCallNumber >= {value} or typedCallNumber < {value}", "308 H977"))
      .param("limit", "15")
      .param("highlightMatch", "true")
      .param("precedingRecordsCount", "5")
      .param("expandAll", "true");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    var expected = cnBrowseResult(2, List.of(
      cnBrowseItem(instance("instance #01"), "308 H977", true)
    ));
    expected.setItems(CallNumberUtils.excludeIrrelevantResultItems("dewey", expected.getItems()));
    assertThat(actual).isEqualTo(expected);

    disableFeature(BROWSE_CN_INTERMEDIATE_VALUES);
  }

  @Test
  void browseByCallNumber_browsingAroundWithDisabledIntermediateValuesAndWithoutType() {
    var request = get(instanceCallNumberBrowsePath())
      .param("query", prepareQuery("callNumber >= {value} or callNumber < {value}", "308 H977"))
      .param("limit", "15")
      .param("highlightMatch", "true")
      .param("precedingRecordsCount", "5")
      .param("expandAll", "true");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    var expected = cnBrowseResult(3, List.of(
      cnBrowseItem(instance("instance #01"), "308 H977", true),
      cnBrowseItem(instance("instance #02"), "Z669.R360 197"),
      cnBrowseItem(instance("instance #01"), "Z669.R360 1975"),
      cnBrowseItem(instance("instance #01"), "Z669.R360 1977")
    ));
    assertThat(actual).isEqualTo(expected);
  }

  private static Instance[] instances() {
    return new Instance[] {
      instances(callNumberBrowseInstanceData()),
      instanceNew(additionalCallNumberBrowseInstanceData())
    };
  }

  @SuppressWarnings("unchecked")
  private static Instance instances(List<List<String>> data) {
    var items = data.stream().map(d -> new Item()
        .id(randomId())
        .tenantId(TENANT_ID)
        .discoverySuppress(false)
        .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents()
          .callNumber(d.get(1))
          .typeId(d.get(0)))
        .effectiveShelvingOrder(getShelfKeyFromCallNumber(d.get(1))))
      .toList();

    return new Instance()
      .id(randomId())
      .title("instance #01")
      .staffSuppress(false)
      .discoverySuppress(false)
      .isBoundWith(false)
      .shared(false)
      .tenantId(TENANT_ID)
      .items(items)
      .holdings(emptyList());
  }

  @SuppressWarnings("unchecked")
  private static Instance instanceNew(List<List<String>> data) {
    var item = data.stream().map(d -> new Item()
        .id(randomId())
        .tenantId(TENANT_ID)
        .discoverySuppress(false)
        .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents()
          .callNumber(d.get(1))
          .typeId(d.get(0)))
        .effectiveShelvingOrder(getShelfKeyFromCallNumber(d.get(1))))
      .toList();

    return new Instance()
      .id(randomId())
      .title("instance #02")
      .staffSuppress(false)
      .discoverySuppress(false)
      .isBoundWith(false)
      .shared(false)
      .tenantId(TENANT_ID)
      .items(item)
      .holdings(emptyList());
  }

  private static Instance instance(String title) {
    return INSTANCE_MAP.get(title);
  }

  private static List<List<String>> callNumberBrowseInstanceData() {
    return List.of(
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 1977"),
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 1975"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H977")
    );
  }

  private static List<List<String>> additionalCallNumberBrowseInstanceData() {
    return List.of(
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 197")
    );
  }
}
