package org.folio.api.consortiumsearch;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.Pair.pair;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.consortiumLocationsSearchPath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;

import java.util.List;
import java.util.Objects;
import org.assertj.core.groups.Tuple;
import org.folio.search.domain.dto.ConsortiumLocation;
import org.folio.search.domain.dto.ConsortiumLocationCollection;
import org.folio.search.model.Pair;
import org.folio.support.base.BaseSharedTest;
import org.folio.support.testdata.SharedTestDataManager;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

public abstract class ConsortiumSearchLocationsIT extends BaseSharedTest {

  private static final int EXPECTED_WITH_SINGLE_TENANT = SharedTestDataManager.locationsCount();
  private static final int EXPECTED_WITH_TWO_TENANTS = EXPECTED_WITH_SINGLE_TENANT * 2;

  @Test
  void doGetConsortiumLocations_returns200AndRecords() {
    List<Pair<String, String>> queryParams = List.of();

    var result = doGet(consortiumLocationsSearchPath(queryParams), MEMBER_TENANT_ID);
    var actual = parseResponse(result, ConsortiumLocationCollection.class);

    assertThat(actual.getLocations()).hasSize(EXPECTED_WITH_TWO_TENANTS);
    assertThat(actual.getTotalRecords()).isEqualTo(EXPECTED_WITH_TWO_TENANTS);
    assertThat(actual.getLocations())
      .filteredOn(location -> Objects.equals(location.getTenantId(), MEMBER_TENANT_ID))
      .hasSize(EXPECTED_WITH_SINGLE_TENANT);
    assertThat(actual.getLocations())
      .filteredOn(location -> Objects.equals(location.getTenantId(), CENTRAL_TENANT_ID))
      .hasSize(EXPECTED_WITH_SINGLE_TENANT);
    assertLocationFieldsNotEmpty(actual.getLocations());
    assertThat(actual.getLocations())
      .map(ConsortiumLocation::getMetadata)
      .filteredOn(metadata -> metadata.getCreatedDate() != null && metadata.getUpdatedDate() != null)
      .hasSize(EXPECTED_WITH_TWO_TENANTS);

    assertThat(actual.getLocations())
      .filteredOn(location -> "true".equals(location.getIsActive()) && isNotEmpty(location.getServicePointIds()))
      .filteredOn(location -> List.of(MEMBER_TENANT_ID, CENTRAL_TENANT_ID).contains(location.getTenantId()))
      .hasSize(12);
  }

  @Test
  void doGetConsortiumLocations_returns200AndRecords_withTenantAndSortQueryParams() {
    List<Pair<String, String>> queryParams = List.of(
      pair("tenantId", "consortium"),
      pair(LIMIT_PARAM, "5"),
      pair(OFFSET_PARAM, "0"),
      pair("sortBy", "name"),
      pair("sortOrder", "asc")
    );

    var result = doGet(consortiumLocationsSearchPath(queryParams), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumLocationCollection.class);

    assertThat(actual.getLocations()).hasSize(5);
    assertThat(actual.getTotalRecords()).isEqualTo(EXPECTED_WITH_SINGLE_TENANT);
    assertThat(actual.getLocations().get(0).getTenantId()).isEqualTo(CENTRAL_TENANT_ID);
    //check sortBy name
    assertThat(actual.getLocations().get(0).getName()).isEqualTo("Annex");
    assertThat(actual.getLocations().get(1).getName()).isEqualTo("DCB");
  }

  @Test
  void doGetConsortiumLocations_returns200AndRecords_withAllQueryParams() {
    List<Pair<String, String>> queryParams = List.of(
      pair("tenantId", "consortium"),
      pair("id", "53cf956f-c1df-410b-8bea-27f712cca7c0"),
      pair(LIMIT_PARAM, "5"),
      pair(OFFSET_PARAM, "0"),
      pair("sortBy", "name"),
      pair("sortOrder", "asc")
    );

    var result = doGet(consortiumLocationsSearchPath(queryParams), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumLocationCollection.class);

    assertThat(actual.getLocations()).hasSize(1);
    assertThat(actual.getTotalRecords()).isEqualTo(1);
    assertThat(actual.getLocations().getFirst().getTenantId()).isEqualTo(CENTRAL_TENANT_ID);
    assertThat(actual.getLocations().getFirst().getName()).isEqualTo("Annex");
    assertThat(actual.getLocations().getFirst().getCode()).isEqualTo("KU/CC/DI/A");
  }

  private void assertLocationFieldsNotEmpty(List<ConsortiumLocation> actualLocations) {
    assertThat(actualLocations)
      .extracting(ConsortiumLocation::getId, ConsortiumLocation::getName, ConsortiumLocation::getTenantId,
        ConsortiumLocation::getInstitutionId, ConsortiumLocation::getCampusId, ConsortiumLocation::getLibraryId,
        ConsortiumLocation::getPrimaryServicePoint)
      .map(Tuple::toList)
      .matches(locations -> locations.stream().allMatch(obj -> StringUtils.isNotBlank(obj.toString())));
  }
}
