package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.folio.search.support.base.ApiEndpoints.authoritySearchPath;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.X_OKAPI_TENANT_HEADER;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.searchResult;
import static org.folio.search.utils.TestUtils.searchServiceRequest;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.OutputStream;
import java.util.List;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.ResourceId;
import org.folio.search.domain.dto.ResourceIds;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.service.ResourceIdService;
import org.folio.search.service.ResourceIdsStreamHelper;
import org.folio.search.service.SearchService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@WebMvcTest(AuthorityController.class)
@Import({ApiExceptionHandler.class, ResourceIdsStreamHelper.class})
class AuthorityControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private SearchService searchService;
  @MockBean private ResourceIdService resourceIdService;

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
  void getAuthorityIds_positive() throws Exception {
    var cqlQuery = "cql.allRecords=1";
    var request = CqlResourceIdsRequest.of(AUTHORITY_RESOURCE, TENANT_ID, cqlQuery, ID_FIELD);

    doAnswer(inv -> {
      var out = (OutputStream) inv.getArgument(1);
      var resourceIds = new ResourceIds().totalRecords(1).ids(List.of(new ResourceId().id(RESOURCE_ID)));
      out.write(OBJECT_MAPPER.writeValueAsBytes(resourceIds));
      return null;
    }).when(resourceIdService).streamResourceIds(eq(request), any(OutputStream.class));

    var requestBuilder = get("/search/authorities/ids")
      .queryParam("query", cqlQuery)
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.ids[*].id", is(List.of(RESOURCE_ID))));
  }
}
