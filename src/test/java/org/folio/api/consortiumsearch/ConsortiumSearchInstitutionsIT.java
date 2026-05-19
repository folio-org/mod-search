package org.folio.api.consortiumsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.Pair.pair;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.consortiumInstitutionsSearchPath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;

import java.util.List;
import java.util.Objects;
import org.assertj.core.groups.Tuple;
import org.folio.search.domain.dto.ConsortiumInstitution;
import org.folio.search.domain.dto.ConsortiumInstitutionCollection;
import org.folio.search.model.Pair;
import org.folio.support.base.BaseSharedTest;
import org.folio.support.testdata.SharedTestDataManager;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

public abstract class ConsortiumSearchInstitutionsIT extends BaseSharedTest {

  private static final int EXPECTED_WITH_SINGLE_TENANT = SharedTestDataManager.institutionsCount();
  private static final int EXPECTED_WITH_TWO_TENANTS = EXPECTED_WITH_SINGLE_TENANT * 2;

  @Test
  void doGetConsortiumInstitutions_returns200AndRecords() {
    List<Pair<String, String>> queryParams = List.of();

    var result = doGet(consortiumInstitutionsSearchPath(queryParams), MEMBER_TENANT_ID);
    var actual = parseResponse(result, ConsortiumInstitutionCollection.class);

    assertThat(actual.getInstitutions()).hasSize(EXPECTED_WITH_TWO_TENANTS);

    assertThat(actual.getTotalRecords()).isEqualTo(EXPECTED_WITH_TWO_TENANTS);

    assertThat(actual.getInstitutions())
      .filteredOn(location -> Objects.equals(location.getTenantId(), MEMBER_TENANT_ID))
      .hasSize(EXPECTED_WITH_SINGLE_TENANT);

    assertThat(actual.getInstitutions())
      .filteredOn(location -> Objects.equals(location.getTenantId(), CENTRAL_TENANT_ID))
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
      pair(LIMIT_PARAM, "5"),
      pair(OFFSET_PARAM, "0"),
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
      pair(LIMIT_PARAM, "5"),
      pair(OFFSET_PARAM, "0"),
      pair("sortBy", "name"),
      pair("sortOrder", "asc")
    );

    var result = doGet(consortiumInstitutionsSearchPath(queryParams), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumInstitutionCollection.class);

    assertThat(actual.getInstitutions()).hasSize(1);
    assertThat(actual.getTotalRecords()).isEqualTo(1);
    assertThat(actual.getInstitutions().getFirst().getTenantId()).isEqualTo(CENTRAL_TENANT_ID);
    assertThat(actual.getInstitutions().getFirst().getName()).isEqualTo("My institution 1");
    assertThat(actual.getInstitutions().getFirst().getCode()).isEqualTo("MI1");
  }
}
