package org.folio.api.consortiumsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.Pair.pair;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.consortiumLibrariesSearchPath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;

import java.util.List;
import org.assertj.core.groups.Tuple;
import org.folio.search.domain.dto.ConsortiumLibrary;
import org.folio.search.domain.dto.ConsortiumLibraryCollection;
import org.folio.search.model.Pair;
import org.folio.support.base.BaseSharedTest;
import org.folio.support.testdata.SharedTestDataManager;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

public abstract class ConsortiumSearchLibrariesIT extends BaseSharedTest {

  private static final int EXPECTED_WITH_SINGLE_TENANT = SharedTestDataManager.librariesCount();
  private static final int EXPECTED_WITH_TWO_TENANTS = EXPECTED_WITH_SINGLE_TENANT * 2;

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
  void doGetConsortiumLibraries_returns200AndRecords_withTenantAndSortQueryParams() {
    List<Pair<String, String>> queryParams = List.of(
      pair("tenantId", "consortium"),
      pair(LIMIT_PARAM, "5"),
      pair(OFFSET_PARAM, "0"),
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

  @Test
  void doGetConsortiumLibraries_returns200AndRecords_withAllQueryParams() {
    List<Pair<String, String>> queryParams = List.of(
      pair("tenantId", "consortium"),
      pair("id", "83891666-dcb6-4cd7-ad3a-f4b305abfe21"),
      pair(LIMIT_PARAM, "5"),
      pair(OFFSET_PARAM, "0"),
      pair("sortBy", "name"),
      pair("sortOrder", "asc")
    );

    var result = doGet(consortiumLibrariesSearchPath(queryParams), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumLibraryCollection.class);

    assertThat(actual.getLibraries()).hasSize(1);
    assertThat(actual.getTotalRecords()).isEqualTo(1);
    assertThat(actual.getLibraries().getFirst().getTenantId()).isEqualTo(CENTRAL_TENANT_ID);
    assertThat(actual.getLibraries().getFirst().getName()).isEqualTo("My library 1");
    assertThat(actual.getLibraries().getFirst().getCode()).isEqualTo("ML1");
  }
}
