package org.folio.search.controller;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.model.Pair.pair;
import static org.folio.search.model.types.ResourceType.LOCATION;
import static org.folio.search.sample.SampleLocations.getLocationsSampleAsMap;
import static org.folio.search.support.base.ApiEndpoints.consortiumLocationsSearchPath;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestConstants.inventoryLocationTopic;
import static org.folio.search.utils.TestUtils.kafkaResourceEvent;
import static org.folio.search.utils.TestUtils.parseResponse;

import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.folio.search.domain.dto.ConsortiumLocation;
import org.folio.search.domain.dto.ConsortiumLocationCollection;
import org.folio.search.model.Pair;
import org.folio.search.support.base.BaseConsortiumIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

@IntegrationTest
class ConsortiumSearchLocationsIT extends BaseConsortiumIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(CENTRAL_TENANT_ID);
    setUpTenant(MEMBER_TENANT_ID);
    saveLocationRecords();
  }

  @AfterAll
  static void cleanUp() {
    removeTenant(MEMBER_TENANT_ID);
    removeTenant(CENTRAL_TENANT_ID);
  }

  @Test
  void doGetConsortiumLocations_returns200AndRecords() {
    List<Pair<String, String>> queryParams = List.of();

    var result = doGet(consortiumLocationsSearchPath(queryParams), MEMBER_TENANT_ID);
    var actual = parseResponse(result, ConsortiumLocationCollection.class);

    assertThat(actual.getLocations()).hasSize(14);
    assertThat(actual.getTotalRecords()).isEqualTo(14);
    assertThat(actual.getLocations())
      .filteredOn(location -> location.getTenantId().equals(MEMBER_TENANT_ID))
      .hasSize(7);
    assertThat(actual.getLocations())
      .filteredOn(location -> location.getTenantId().equals(CENTRAL_TENANT_ID))
      .hasSize(7);
    assertThat(actual.getLocations())
      .extracting(ConsortiumLocation::getId, ConsortiumLocation::getName, ConsortiumLocation::getTenantId,
        ConsortiumLocation::getInstitutionId, ConsortiumLocation::getCampusId, ConsortiumLocation::getLibraryId,
        ConsortiumLocation::getPrimaryServicePoint)
      .map(Tuple::toList)
      .matches(locations -> locations.stream().allMatch(obj -> StringUtils.isNotBlank(obj.toString())));
    assertThat(actual.getLocations())
      .map(ConsortiumLocation::getMetadata)
      .filteredOn(metadata -> metadata.getCreatedDate() != null && metadata.getUpdatedDate() != null)
      .hasSize(14);

    assertThat(actual.getLocations())
      .filteredOn(location -> "true".equals(location.getIsActive()) && isNotEmpty(location.getServicePointIds()))
      .filteredOn(location -> List.of(MEMBER_TENANT_ID, CENTRAL_TENANT_ID).contains(location.getTenantId()))
      .hasSize(12);
  }

  @Test
  void doGetConsortiumLocations_returns200AndRecords_withAllQueryParams() {
    List<Pair<String, String>> queryParams = List.of(
      pair("tenantId", "consortium"),
      pair("limit", "5"),
      pair("offset", "0"),
      pair("sortBy", "name"),
      pair("sortOrder", "asc")
    );

    var result = doGet(consortiumLocationsSearchPath(queryParams), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumLocationCollection.class);

    assertThat(actual.getLocations()).hasSize(5);
    assertThat(actual.getTotalRecords()).isEqualTo(7);
    assertThat(actual.getLocations().get(0).getTenantId()).isEqualTo(CENTRAL_TENANT_ID);
    //check sortBy name
    assertThat(actual.getLocations().get(0).getName()).isEqualTo("Annex");
    assertThat(actual.getLocations().get(1).getName()).isEqualTo("DCB");
  }

  private static void saveLocationRecords() {
    getLocationsSampleAsMap().stream()
      .flatMap(location -> Stream.of(
        kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, location, null),
        kafkaResourceEvent(MEMBER_TENANT_ID, CREATE, location, null)))
      .forEach(event -> kafkaTemplate.send(inventoryLocationTopic(event.getTenant()), event));

    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var totalHits = countIndexDocument(LOCATION, CENTRAL_TENANT_ID);
      assertThat(totalHits).isEqualTo(14);
    });
  }
}
