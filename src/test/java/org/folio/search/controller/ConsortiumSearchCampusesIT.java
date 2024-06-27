package org.folio.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.model.Pair.pair;
import static org.folio.search.sample.SampleCampuses.getCampusesSampleAsMap;
import static org.folio.search.support.base.ApiEndpoints.consortiumCampusesSearchPath;
import static org.folio.search.utils.SearchUtils.CAMPUS_RESOURCE;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestConstants.inventoryCampusTopic;
import static org.folio.search.utils.TestUtils.kafkaResourceEvent;
import static org.folio.search.utils.TestUtils.parseResponse;

import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.folio.search.domain.dto.ConsortiumCampus;
import org.folio.search.domain.dto.ConsortiumCampusCollection;
import org.folio.search.model.Pair;
import org.folio.search.support.base.BaseConsortiumIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
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
      .filteredOn(location -> location.getTenantId().equals(MEMBER_TENANT_ID))
      .hasSize(EXPECTED_WITH_SINGLE_TENANT);

    assertThat(actual.getCampuses())
      .filteredOn(location -> location.getTenantId().equals(CENTRAL_TENANT_ID))
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
  void doGetConsortiumCampuses_returns200AndRecords_withAllQueryParams() {
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

  private static void saveCampusRecords() {
    getCampusesSampleAsMap().stream()
      .flatMap(campus -> Stream.of(
        kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, campus, null),
        kafkaResourceEvent(MEMBER_TENANT_ID, CREATE, campus, null)))
      .forEach(event -> kafkaTemplate.send(inventoryCampusTopic(event.getTenant()), event));

    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var totalHits = countIndexDocument(CAMPUS_RESOURCE, CENTRAL_TENANT_ID);

      assertThat(totalHits).isEqualTo(EXPECTED_WITH_TWO_TENANTS);
    });
  }
}
