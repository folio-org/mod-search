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
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.domain.dto.Metadata;
import org.folio.search.domain.dto.Tags;
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
  void browseByCallNumber_browsingAroundWithDisabledIntermediateValuesAndLowLimit() {
    var limit = 3;
    var request = get(instanceCallNumberBrowsePath())
      .param("callNumberType", "dewey")
      .param("query", prepareQuery("typedCallNumber >= {value} or typedCallNumber < {value}", "308 H977"))
      .param("limit", String.valueOf(limit))
      .param("highlightMatch", "true")
      .param("precedingRecordsCount", "1")
      .param("expandAll", "true");
    var result = parseResponse(doGet(request), CallNumberBrowseResult.class);
    assertThat(result.getItems()).hasSizeLessThanOrEqualTo(limit);
  }

  private static Instance[] instances() {
    return new Instance[] {
      instance(callNumberBrowseInstanceData(), "instance #01"),
      instance(callNumberBrowseInstanceData(),"instance #02"),
      instanceWithHoldings(callNumberBrowseInstanceDataForHoldings(), "instance #03")
    };
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

  private static Instance instance(String title) {
    return INSTANCE_MAP.get(title);
  }

  private static Instance instanceWithHoldings(List<List<String>> data, String title) {
    var holdings = data.stream().map(d -> new Holding()
        .id(randomId())
        .tenantId(TENANT_ID)
        .discoverySuppress(false)
        .callNumber(d.get(1))
        .holdingsTypeId("eb003b9d-86f2-4bdf-9f8e-28851122617d")
        .permanentLocationId("765b4c3b-485c-4ce4-a117-f99c01ac49fe")
        .metadata(metadata("2021-03-01T00:00:00.000+00:00", "2021-03-05T12:30:00.000+00:00"))
        .tags(new Tags().tagList(List.of("tag1", "tag2"))))
      .toList();

    return new Instance()
      .id(randomId())
      .title(title)
      .staffSuppress(false)
      .discoverySuppress(false)
      .isBoundWith(false)
      .shared(false)
      .tenantId(TENANT_ID)
      .items(emptyList())
      .holdings(holdings);
  }

  private static List<List<String>> callNumberBrowseInstanceData() {
    return List.of(
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 1977"),
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 1975"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H977")
    );
  }

  private static List<List<String>> callNumberBrowseInstanceDataForHoldings() {
    return List.of(
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 1970"),
      List.of("95467209-6d7b-468b-94df-0f5d7ad2747d", "Z669.R360 1971"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H971"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H972"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H973"),
      List.of("03dd64d0-5626-4ecd-8ece-4531e0069f35", "308 H977")
    );
  }

  private static Metadata metadata(String createdDate, String updatedDate) {
    return new Metadata().createdDate(createdDate).updatedDate(updatedDate);
  }
}
