package org.folio.search.controller;

import org.folio.search.domain.dto.ConsortiumLocationCollection;
import org.folio.search.domain.dto.Location;
import org.folio.search.model.Pair;
import org.folio.search.support.base.BaseConsortiumIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.Pair.pair;
import static org.folio.search.support.base.ApiEndpoints.consortiumLocationsSearchPath;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestUtils.parseResponse;

@IntegrationTest
public class ConsortiumSearchLocationsIT extends BaseConsortiumIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(MEMBER_TENANT_ID);
    setUpTenant(Location.class, CENTRAL_TENANT_ID, getLocationsSampleAsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }


  @Test
  void doGetConsortiumLocations_returns200AndRecords_withAllQueryParams() {
    List<Pair<String, String>> queryParams = List.of(
      pair("tenantId", CENTRAL_TENANT_ID),
      pair("limit", "1"),
      pair("offset", "1"),
      pair("sortBy", "name"),
      pair("sortOrder", "asc")
    );

    var result = doGet(consortiumLocationsSearchPath(queryParams), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumLocationCollection.class);

    assertThat(actual.getTotalRecords()).isEqualTo(1);
  }

}
