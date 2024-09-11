package org.folio.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.model.Pair.pair;
import static org.folio.search.sample.SampleLibraries.getLibrariesSampleAsMap;
import static org.folio.search.support.base.ApiEndpoints.consortiumLibrariesSearchPath;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestConstants.inventoryLibraryTopic;
import static org.folio.search.utils.TestUtils.kafkaResourceEvent;
import static org.folio.search.utils.TestUtils.parseResponse;

import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.folio.search.domain.dto.ConsortiumLibrary;
import org.folio.search.domain.dto.ConsortiumLibraryCollection;
import org.folio.search.model.Pair;
import org.folio.search.model.types.ResourceType;
import org.folio.search.support.base.BaseConsortiumIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

@IntegrationTest
class ConsortiumSearchLibrariesIT extends BaseConsortiumIntegrationTest {

  private static final int EXPECTED_WITH_TWO_TENANTS = 18;
  private static final int EXPECTED_WITH_SINGLE_TENANT = 9;

  @BeforeAll
  static void prepare() {
    setUpTenant(CENTRAL_TENANT_ID);
    setUpTenant(MEMBER_TENANT_ID);
    saveLibraryRecords();
  }

  @AfterAll
  static void cleanUp() {
    removeTenant(MEMBER_TENANT_ID);
    removeTenant(CENTRAL_TENANT_ID);
  }

  @Test
  void doGetConsortiumLibraries_returns200AndRecords() {
    List<Pair<String, String>> queryParams = List.of();

    var result = doGet(consortiumLibrariesSearchPath(queryParams), MEMBER_TENANT_ID);
    var actual = parseResponse(result, ConsortiumLibraryCollection.class);

    assertThat(actual.getLibraries()).hasSize(EXPECTED_WITH_TWO_TENANTS);

    assertThat(actual.getTotalRecords()).isEqualTo(EXPECTED_WITH_TWO_TENANTS);

    assertThat(actual.getLibraries())
      .filteredOn(location -> location.getTenantId().equals(MEMBER_TENANT_ID))
      .hasSize(EXPECTED_WITH_SINGLE_TENANT);

    assertThat(actual.getLibraries())
      .filteredOn(location -> location.getTenantId().equals(CENTRAL_TENANT_ID))
      .hasSize(EXPECTED_WITH_SINGLE_TENANT);

    assertThat(actual.getLibraries())
      .extracting(ConsortiumLibrary::getId, ConsortiumLibrary::getName, ConsortiumLibrary::getTenantId,
        ConsortiumLibrary::getCampusId)
      .map(Tuple::toList)
      .matches(locations -> locations.stream().allMatch(obj -> StringUtils.isNotBlank(obj.toString())));

    assertThat(actual.getLibraries())
      .map(ConsortiumLibrary::getMetadata)
      .filteredOn(metadata -> metadata.getCreatedDate() != null && metadata.getUpdatedDate() != null)
      .hasSize(EXPECTED_WITH_TWO_TENANTS);
  }

  @Test
  void doGetConsortiumLibraries_returns200AndRecords_withAllQueryParams() {
    List<Pair<String, String>> queryParams = List.of(
      pair("tenantId", "consortium"),
      pair("limit", "5"),
      pair("offset", "0"),
      pair("sortBy", "name"),
      pair("sortOrder", "asc")
    );

    var result = doGet(consortiumLibrariesSearchPath(queryParams), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumLibraryCollection.class);

    assertThat(actual.getLibraries()).hasSize(5);
    assertThat(actual.getTotalRecords()).isEqualTo(EXPECTED_WITH_SINGLE_TENANT);
    assertThat(actual.getLibraries().get(0).getTenantId()).isEqualTo(CENTRAL_TENANT_ID);
    // check sortBy name
    assertThat(actual.getLibraries().get(0).getName()).isEqualTo("My library 1");
    assertThat(actual.getLibraries().get(1).getName()).isEqualTo("My library 2");
  }

  private static void saveLibraryRecords() {
    getLibrariesSampleAsMap().stream()
      .flatMap(library -> Stream.of(
        kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, library, null),
        kafkaResourceEvent(MEMBER_TENANT_ID, CREATE, library, null)))
      .forEach(event -> kafkaTemplate.send(inventoryLibraryTopic(event.getTenant()), event));

    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var totalHits = countIndexDocument(ResourceType.LIBRARY, CENTRAL_TENANT_ID);

      assertThat(totalHits).isEqualTo(EXPECTED_WITH_TWO_TENANTS);
    });
  }
}
