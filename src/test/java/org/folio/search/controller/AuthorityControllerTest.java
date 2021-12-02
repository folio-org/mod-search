package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.folio.search.support.base.ApiEndpoints.authoritySearchPath;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.X_OKAPI_TENANT_HEADER;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.facet;
import static org.folio.search.utils.TestUtils.facetItem;
import static org.folio.search.utils.TestUtils.facetResult;
import static org.folio.search.utils.TestUtils.facetServiceRequest;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.searchResult;
import static org.folio.search.utils.TestUtils.searchServiceRequest;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.folio.search.domain.dto.Authority;
import org.folio.search.service.FacetService;
import org.folio.search.service.SearchService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@Import({ApiExceptionHandler.class})
@WebMvcTest(AuthorityController.class)
class AuthorityControllerTest {

  @MockBean
  private SearchService searchService;
  @MockBean
  private FacetService facetService;
  @Autowired
  private MockMvc mockMvc;

  @Test
  void search_positive() throws Exception {
    var cqlQuery = "cql.allRecords=1";
    var expectedSearchRequest = searchServiceRequest(Authority.class, cqlQuery);

    when(searchService.search(expectedSearchRequest)).thenReturn(searchResult());

    var requestBuilder = get(authoritySearchPath())
      .queryParam("query", cqlQuery)
      .queryParam("limit", "100")
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.authorities", is(emptyList())));
  }

  @Test
  void getFacets_positive() throws Exception {
    var cqlQuery = "headingType all \"test-query\"";
    var expectedFacetRequest = facetServiceRequest(AUTHORITY_RESOURCE, cqlQuery, "source:5");
    when(facetService.getFacets(expectedFacetRequest)).thenReturn(
      facetResult(mapOf("source", facet(List.of(facetItem("Personal Name", 20), facetItem("Corporate Name", 10))))));

    var requestBuilder = get("/search/authorities/facets")
      .queryParam("query", cqlQuery)
      .queryParam("facet", "source:5")
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.facets.source.totalRecords", is(2)))
      .andExpect(jsonPath("$.facets.source.values[0].id", is("Personal Name")))
      .andExpect(jsonPath("$.facets.source.values[0].totalRecords", is(20)))
      .andExpect(jsonPath("$.facets.source.values[1].id", is("Corporate Name")))
      .andExpect(jsonPath("$.facets.source.values[1].totalRecords", is(10)));
  }
}
