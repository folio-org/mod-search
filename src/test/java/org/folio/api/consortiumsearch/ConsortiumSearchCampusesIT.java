package org.folio.api.consortiumsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.model.Pair.pair;
import static org.folio.search.model.types.ResourceType.CAMPUS;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.TestConstants.inventoryCampusTopic;
import static org.folio.support.base.ApiEndpoints.consortiumCampusesSearchPath;
import static org.folio.support.sample.SampleCampuses.getCampusesSampleAsMap;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.kafkaResourceEvent;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.folio.search.domain.dto.ConsortiumCampus;
import org.folio.search.domain.dto.ConsortiumCampusCollection;
import org.folio.search.model.Pair;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseConsortiumIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

@IntegrationTest
class ConsortiumSearchCampusesIT extends BaseConsortiumIntegrationTest {

  private static final int EXPECTED_WITH_TWO_TENANTS = 18;
  private static final int EXPECTED_WITH_SINGLE_TENANT = 9;

  @BeforeAll
  static void prepare() {
    setUpTenant(CENTRAL_TENANT_ID);
    setUpTenant(MEMBER_TENANT_ID);
    saveCampusRecords();
  }

  @AfterAll
  static void cleanUp() {
    removeTenant(MEMBER_TENANT_ID);
    removeTenant(CENTRAL_TENANT_ID);
  }

  @Test
  void doGetConsortiumCampuses_returns200AndRecords() {
    List<Pair<String, String>> queryParams = List.of();

    var result = doGet(consortiumCampusesSearchPath(queryParams), MEMBER_TENANT_ID);
    var actual = parseResponse(result, ConsortiumCampusCollection.class);

    assertThat(actual.getCampuses()).hasSize(EXPECTED_WITH_TWO_TENANTS);

    assertThat(actual.getTotalRecords()).isEqualTo(EXPECTED_WITH_TWO_TENANTS);

    assertThat(actual.getCampuses())
      .filteredOn(location -> Objects.equals(location.getTenantId(), MEMBER_TENANT_ID))
      .hasSize(EXPECTED_WITH_SINGLE_TENANT);

    assertThat(actual.getCampuses())
      .filteredOn(location -> Objects.equals(location.getTenantId(), CENTRAL_TENANT_ID))
      .hasSize(EXPECTED_WITH_SINGLE_TENANT);

    assertThat(actual.getCampuses())
      .extracting(ConsortiumCampus::getId, ConsortiumCampus::getName, ConsortiumCampus::getTenantId,
        ConsortiumCampus::getInstitutionId)
      .map(Tuple::toList)
      .matches(locations -> locations.stream().allMatch(obj -> StringUtils.isNotBlank(obj.toString())));

    assertThat(actual.getCampuses())
      .map(ConsortiumCampus::getMetadata)
      .filteredOn(metadata -> metadata.getCreatedDate() != null && metadata.getUpdatedDate() != null)
      .hasSize(EXPECTED_WITH_TWO_TENANTS);
  }

  @Test
  void doGetConsortiumCampuses_returns200AndRecords_withTenantAndSortQueryParams() {
    List<Pair<String, String>> queryParams = List.of(
      pair("tenantId", "consortium"),
      pair("limit", "5"),
      pair("offset", "0"),
      pair("sortBy", "name"),
      pair("sortOrder", "asc")
    );

    var result = doGet(consortiumCampusesSearchPath(queryParams), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumCampusCollection.class);

    assertThat(actual.getCampuses()).hasSize(5);
    assertThat(actual.getTotalRecords()).isEqualTo(EXPECTED_WITH_SINGLE_TENANT);
    assertThat(actual.getCampuses().get(0).getTenantId()).isEqualTo(CENTRAL_TENANT_ID);
    // check sortBy name
    assertThat(actual.getCampuses().get(0).getName()).isEqualTo("My campus 1");
    assertThat(actual.getCampuses().get(1).getName()).isEqualTo("My campus 2");
  }

  @Test
  void doGetConsortiumCampuses_returns200AndRecords_withAllQueryParams() {
    List<Pair<String, String>> queryParams = List.of(
        pair("tenantId", "consortium"),
        pair("id", "83891666-dcb6-4cd7-ad3a-f4b305abfe21"),
        pair("limit", "5"),
        pair("offset", "0"),
        pair("sortBy", "name"),
        pair("sortOrder", "asc")
    );

    var result = doGet(consortiumCampusesSearchPath(queryParams), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumCampusCollection.class);

    assertThat(actual.getCampuses()).hasSize(1);
    assertThat(actual.getTotalRecords()).isEqualTo(1);
    var campus = actual.getCampuses().getFirst();
    assertThat(campus.getTenantId()).isEqualTo(CENTRAL_TENANT_ID);
    assertThat(campus.getName()).isEqualTo("My campus 1");
    assertThat(campus.getCode()).isEqualTo("MC1");
  }

  private static void saveCampusRecords() {
    getCampusesSampleAsMap().stream()
      .flatMap(campus -> Stream.of(
        kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, campus, null),
        kafkaResourceEvent(MEMBER_TENANT_ID, CREATE, campus, null)))
      .forEach(event -> kafkaTemplate.send(inventoryCampusTopic(event.getTenant()), event));

    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var totalHits = countIndexDocument(CAMPUS, CENTRAL_TENANT_ID);

      assertThat(totalHits).isEqualTo(EXPECTED_WITH_TWO_TENANTS);
    });
  }
}
