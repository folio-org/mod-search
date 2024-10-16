package org.folio.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.model.Pair.pair;
import static org.folio.search.sample.SampleInstitutions.getInstitutionsSampleAsMap;
import static org.folio.search.support.base.ApiEndpoints.consortiumInstitutionsSearchPath;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestConstants.inventoryInstitutionTopic;
import static org.folio.search.utils.TestUtils.kafkaResourceEvent;
import static org.folio.search.utils.TestUtils.parseResponse;

import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.folio.search.domain.dto.ConsortiumInstitution;
import org.folio.search.domain.dto.ConsortiumInstitutionCollection;
import org.folio.search.model.Pair;
import org.folio.search.model.types.ResourceType;
import org.folio.search.support.base.BaseConsortiumIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

@IntegrationTest
class ConsortiumSearchInstitutionsIT extends BaseConsortiumIntegrationTest {

  private static final int EXPECTED_WITH_TWO_TENANTS = 18;
  private static final int EXPECTED_WITH_SINGLE_TENANT = 9;

  @BeforeAll
  static void prepare() {
    setUpTenant(CENTRAL_TENANT_ID);
    setUpTenant(MEMBER_TENANT_ID);
    saveInstitutionRecords();
  }

  @AfterAll
  static void cleanUp() {
    removeTenant(MEMBER_TENANT_ID);
    removeTenant(CENTRAL_TENANT_ID);
  }

  @Test
  void doGetConsortiumInstitutions_returns200AndRecords() {
    List<Pair<String, String>> queryParams = List.of();

    var result = doGet(consortiumInstitutionsSearchPath(queryParams), MEMBER_TENANT_ID);
    var actual = parseResponse(result, ConsortiumInstitutionCollection.class);

    assertThat(actual.getInstitutions()).hasSize(EXPECTED_WITH_TWO_TENANTS);

    assertThat(actual.getTotalRecords()).isEqualTo(EXPECTED_WITH_TWO_TENANTS);

    assertThat(actual.getInstitutions())
      .filteredOn(location -> location.getTenantId().equals(MEMBER_TENANT_ID))
      .hasSize(EXPECTED_WITH_SINGLE_TENANT);

    assertThat(actual.getInstitutions())
      .filteredOn(location -> location.getTenantId().equals(CENTRAL_TENANT_ID))
      .hasSize(EXPECTED_WITH_SINGLE_TENANT);

    assertThat(actual.getInstitutions())
      .extracting(ConsortiumInstitution::getId, ConsortiumInstitution::getName, ConsortiumInstitution::getTenantId)
      .map(Tuple::toList)
      .matches(locations -> locations.stream().allMatch(obj -> StringUtils.isNotBlank(obj.toString())));

    assertThat(actual.getInstitutions())
      .map(ConsortiumInstitution::getMetadata)
      .filteredOn(metadata -> metadata.getCreatedDate() != null && metadata.getUpdatedDate() != null)
      .hasSize(EXPECTED_WITH_TWO_TENANTS);
  }

  @Test
  void doGetConsortiumInstitutions_returns200AndRecords_withTenantAndSortQueryParams() {
    List<Pair<String, String>> queryParams = List.of(
      pair("tenantId", "consortium"),
      pair("limit", "5"),
      pair("offset", "0"),
      pair("sortBy", "name"),
      pair("sortOrder", "asc")
    );

    var result = doGet(consortiumInstitutionsSearchPath(queryParams), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumInstitutionCollection.class);

    assertThat(actual.getInstitutions()).hasSize(5);
    assertThat(actual.getTotalRecords()).isEqualTo(EXPECTED_WITH_SINGLE_TENANT);
    assertThat(actual.getInstitutions().get(0).getTenantId()).isEqualTo(CENTRAL_TENANT_ID);
    // check sortBy name
    assertThat(actual.getInstitutions().get(0).getName()).isEqualTo("My institution 1");
    assertThat(actual.getInstitutions().get(1).getName()).isEqualTo("My institution 2");
  }

  @Test
  void doGetConsortiumInstitutions_returns200AndRecords_withAllQueryParams() {
    List<Pair<String, String>> queryParams = List.of(
        pair("tenantId", "consortium"),
        pair("id", "52e92db5-34f4-4741-af40-e23c4d9d3bf1"),
        pair("limit", "5"),
        pair("offset", "0"),
        pair("sortBy", "name"),
        pair("sortOrder", "asc")
    );

    var result = doGet(consortiumInstitutionsSearchPath(queryParams), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumInstitutionCollection.class);

    assertThat(actual.getInstitutions()).hasSize(1);
    assertThat(actual.getTotalRecords()).isEqualTo(1);
    assertThat(actual.getInstitutions().get(0).getTenantId()).isEqualTo(CENTRAL_TENANT_ID);
    assertThat(actual.getInstitutions().get(0).getName()).isEqualTo("My institution 1");
    assertThat(actual.getInstitutions().get(0).getCode()).isEqualTo("MI1");
  }

  private static void saveInstitutionRecords() {
    getInstitutionsSampleAsMap().stream()
      .flatMap(institution -> Stream.of(
        kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, institution, null),
        kafkaResourceEvent(MEMBER_TENANT_ID, CREATE, institution, null)))
      .forEach(event -> kafkaTemplate.send(inventoryInstitutionTopic(event.getTenant()), event));

    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var totalHits = countIndexDocument(ResourceType.INSTITUTION, CENTRAL_TENANT_ID);

      assertThat(totalHits).isEqualTo(EXPECTED_WITH_TWO_TENANTS);
    });
  }
}
