package org.folio.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.controller.SearchConsortiumController.REQUEST_NOT_ALLOWED_MSG;
import static org.folio.search.model.Pair.pair;
import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.sample.SampleInstances.getSemanticWebId;
import static org.folio.search.support.base.ApiEndpoints.consortiumHoldingsSearchPath;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.ConsortiumHoldingCollection;
import org.folio.search.model.Pair;
import org.folio.search.support.base.BaseConsortiumIntegrationTest;
import org.folio.spring.test.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class SearchHoldingsConsortiumIT extends BaseConsortiumIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(CENTRAL_TENANT_ID);
    setUpTenant(MEMBER_TENANT_ID, getSemanticWeb());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void doGetConsortiumHoldings_returns200AndRecords() {
    var result = doGet(consortiumHoldingsSearchPath(), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumHoldingCollection.class);

    assertThat(actual.getTotalRecords()).isEqualTo(3);
    assertThat(actual.getHoldings()).containsExactlyInAnyOrder(getExpectedHoldings());
  }

  @Test
  void doGetConsortiumHoldings_returns200AndRecords_withAllQueryParams() {
    List<Pair<String, String>> queryParams = List.of(
      pair("instanceId", getSemanticWebId()),
      pair("tenantId", MEMBER_TENANT_ID),
      pair("limit", "1"),
      pair("offset", "1"),
      pair("sortBy", "callNumber"),
      pair("sortOrder", "desc")
    );
    var result = doGet(consortiumHoldingsSearchPath(queryParams), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumHoldingCollection.class);

    assertThat(actual.getTotalRecords()).isEqualTo(1);
    assertThat(actual.getHoldings())
      .satisfiesExactly(input -> assertEquals("call number", input.getCallNumber()));
  }

  @Test
  void tryGetConsortiumHoldings_returns400_whenRequestedForNotCentralTenant() throws Exception {
    tryGet(consortiumHoldingsSearchPath())
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is(REQUEST_NOT_ALLOWED_MSG)))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("x-okapi-tenant")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(MEMBER_TENANT_ID)));
  }

  @Test
  void tryGetConsortiumHoldings_returns400_whenOrderBySpecifiedWithoutAnyFilters() throws Exception {
    List<Pair<String, String>> queryParams = List.of(
      pair("limit", "1"),
      pair("offset", "1"),
      pair("sortBy", "callNumber"),
      pair("sortOrder", "desc")
    );
    tryGet(consortiumHoldingsSearchPath(queryParams), CENTRAL_TENANT_ID)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is("At least one filter criteria required")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  private ConsortiumHolding[] getExpectedHoldings() {
    var instance = getSemanticWeb();
    return instance.getHoldings().stream()
      .map(holding -> new ConsortiumHolding()
        .id(holding.getId())
        .hrid(holding.getHrid())
        .tenantId(MEMBER_TENANT_ID)
        .instanceId(instance.getId())
        .callNumberPrefix(holding.getCallNumberPrefix())
        .callNumber(holding.getCallNumber())
        .copyNumber(holding.getCopyNumber())
        .permanentLocationId(holding.getPermanentLocationId())
        .discoverySuppress(holding.getDiscoverySuppress() != null && holding.getDiscoverySuppress()))
      .toArray(ConsortiumHolding[]::new);
  }
}
