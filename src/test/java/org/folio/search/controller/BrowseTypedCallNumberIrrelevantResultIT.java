package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CN_INTERMEDIATE_VALUES;
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
import org.folio.search.utils.CallNumberUtils;
import org.folio.spring.test.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class BrowseTypedCallNumberIrrelevantResultIT extends BaseIntegrationTest {

  private static final Instance[] INSTANCES = instances();
  private static final Map<String, Instance> INSTANCE_MAP =
    Arrays.stream(INSTANCES).collect(toMap(Instance::getTitle, identity()));

  @BeforeAll
  static void prepare() {
    setUpTenant(INSTANCES);
    enableFeature(BROWSE_CN_INTERMEDIATE_VALUES);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void browseByCallNumber_browsingAroundWithEnabledIntermediateValues() {
    var request = get(instanceCallNumberBrowsePath())
      .param("callNumberType", "dewey")
      .param("query", prepareQuery("typedCallNumber >= {value} or typedCallNumber < {value}", "308 H977"))
      .param("limit", "10")
      .param("highlightMatch", "true")
      .param("precedingRecordsCount", "5")
      .param("expandAll", "true");
    var actual = parseResponse(doGet(request), CallNumberBrowseResult.class);
    final CallNumberBrowseResult expected = new CallNumberBrowseResult().totalRecords(18).prev("3308 H976").next("3308 H981").items(List.of(
      cnBrowseItem(INSTANCE_MAP.get("instance #01"), "308 H976"),
      cnBrowseItem(INSTANCE_MAP.get("instance #10"), "308 H970"),
      cnBrowseItem(INSTANCE_MAP.get("instance #11"), "308 H972"),
      cnBrowseItem(INSTANCE_MAP.get("instance #11"), "308 H973"),
      cnBrowseItem(INSTANCE_MAP.get("instance #11"), "308 H974"),
      cnBrowseItem(INSTANCE_MAP.get("instance #09"), "308 H977", true),
      cnBrowseItem(INSTANCE_MAP.get("instance #01"), "308 H978"),
      cnBrowseItem(INSTANCE_MAP.get("instance #10"), "308 H979"),
      cnBrowseItem(INSTANCE_MAP.get("instance #02"), "308 H980"),
      cnBrowseItem(INSTANCE_MAP.get("instance #08"), "308 H981")
    ));
    expected.setItems(CallNumberUtils.excludeIrrelevantResultItems("dewey", expected.getItems()));
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browseByCallNumber_browsingAroundWithDisabledIntermediateValuesAndLowLimit() {
    var limit = 12;
    var request = get(instanceCallNumberBrowsePath())
      .param("callNumberType", "dewey")
      .param("query", prepareQuery("typedCallNumber >= {value} or typedCallNumber < {value}", "308 H977"))
      .param("limit", String.valueOf(limit))
      .param("highlightMatch", "true")
      .param("precedingRecordsCount", "7")
      .param("expandAll", "true");
    var result = parseResponse(doGet(request), CallNumberBrowseResult.class);
    final CallNumberBrowseResult expected = new CallNumberBrowseResult().prev("3308 H971").next("3308 H981").totalRecords(18).items(
      List.of(
        cnBrowseItem(INSTANCE_MAP.get("instance #01"), "308 H971"),
        cnBrowseItem(INSTANCE_MAP.get("instance #18"), "308 H975"),
        cnBrowseItem(INSTANCE_MAP.get("instance #01"), "308 H976"),
        cnBrowseItem(INSTANCE_MAP.get("instance #10"), "308 H970"),
        cnBrowseItem(INSTANCE_MAP.get("instance #11"), "308 H972"),
        cnBrowseItem(INSTANCE_MAP.get("instance #11"), "308 H973"),
        cnBrowseItem(INSTANCE_MAP.get("instance #11"), "308 H974"),
        cnBrowseItem(INSTANCE_MAP.get("instance #09"), "308 H977", true),
        cnBrowseItem(INSTANCE_MAP.get("instance #01"), "308 H978"),
        cnBrowseItem(INSTANCE_MAP.get("instance #10"), "308 H979"),
        cnBrowseItem(INSTANCE_MAP.get("instance #02"), "308 H980"),
        cnBrowseItem(INSTANCE_MAP.get("instance #08"), "308 H981")
      )
    );
    expected.setItems(CallNumberUtils.excludeIrrelevantResultItems("dewey", expected.getItems()));

    assertThat(result.getItems()).hasSizeLessThanOrEqualTo(limit);
    assertThat(result).isEqualTo(expected);
  }

  private static Instance[] instances() {
    Map<String, List<List<String>>> collectedByTitle = callNumberBrowseInstanceData()
      .stream()
      .collect(groupingBy(d -> d.get(2)));

    List<Instance> instanceList = collectedByTitle.keySet()
      .stream()
      .map(k -> instance(collectedByTitle.get(k), k))
      .toList();
    var instanceArray = new Instance[instanceList.size()];
    instanceList.toArray(instanceArray);
    return instanceArray;
  }

  private static Instance instance(List<List<String>> data, String title) {
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
      .title(title)
      .staffSuppress(false)
      .discoverySuppress(false)
      .isBoundWith(false)
      .shared(false)
      .tenantId(TENANT_ID)
      .items(items)
      .holdings(emptyList());
  }

  private static List<List<String>> callNumberBrowseInstanceData() {
    return List.of(
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 1977", "instance #10"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H970", "instance #10"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H971", "instance #01"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H972", "instance #11"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H973", "instance #11"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H974", "instance #11"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H975", "instance #18"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H976", "instance #01"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H977", "instance #09"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H978", "instance #01"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H979", "instance #10"),
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 1976", "instance #02"),
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 1975", "instance #10"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H980", "instance #02"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H981", "instance #08"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H982", "instance #09"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H983", "instance #07"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H984", "instance #06"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H985", "instance #05"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H986", "instance #04"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H987", "instance #30"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H988", "instance #20"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H989", "instance #18"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H990", "instance #81")
    );
  }
}
