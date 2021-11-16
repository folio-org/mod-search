package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.folio.search.controller.IndexControllerTest.INDEX_NAME;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.X_OKAPI_TENANT_HEADER;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.facet;
import static org.folio.search.utils.TestUtils.facetItem;
import static org.folio.search.utils.TestUtils.facetResult;
import static org.folio.search.utils.TestUtils.facetServiceRequest;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
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
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.index.Index;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.ResourceId;
import org.folio.search.domain.dto.ResourceIds;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.service.FacetService;
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
@WebMvcTest(InstanceController.class)
@Import({ApiExceptionHandler.class, ResourceIdsStreamHelper.class})
class InstanceControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private SearchService searchService;
  @MockBean private FacetService facetService;
  @MockBean private ResourceIdService resourceIdService;

  @Test
  void search_positive() throws Exception {

    var cqlQuery = "title all \"test-query\"";

    when(searchService.search(searchServiceRequest(Instance.class, cqlQuery))).thenReturn(searchResult());

    var requestBuilder = get("/search/instances")
      .queryParam("query", cqlQuery)
      .queryParam("limit", "100")
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.instances", is(emptyList())));
  }

  @Test
  void search_negative_indexNotFound() throws Exception {
    var cqlQuery = "title all \"test-query\"";
    var elasticsearchException = new ElasticsearchException("Elasticsearch exception ["
      + "type=index_not_found_exception, "
      + "reason=no such index [instance_test-tenant]]");
    elasticsearchException.setIndex(new Index(INDEX_NAME, randomId()));

    when(searchService.search(searchServiceRequest(Instance.class, cqlQuery))).thenThrow(
      new SearchOperationException("error", elasticsearchException));

    var requestBuilder = get("/search/instances")
      .queryParam("query", cqlQuery)
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Index not found: " + INDEX_NAME)))
      .andExpect(jsonPath("$.errors[0].type", is("ElasticsearchException")))
      .andExpect(jsonPath("$.errors[0].code", is("elasticsearch_error")));
  }

  @Test
  void search_negative_invalidLimitParameter() throws Exception {
    var requestBuilder = get("/search/instances")
      .queryParam("query", "title all \"test-query\"")
      .queryParam("limit", "100000")
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("searchInstances.limit must be less than or equal to 500")))
      .andExpect(jsonPath("$.errors[0].type", is("ConstraintViolationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void search_negative_invalidCqlQuery() throws Exception {
    var cqlQuery = "title all \"test-query\" and";
    var expectedSearchRequest = searchServiceRequest(Instance.class, cqlQuery);
    var exceptionMessage = String.format("Failed to parse CQL query [query: '%s']", cqlQuery);
    when(searchService.search(expectedSearchRequest)).thenThrow(new SearchServiceException(exceptionMessage));

    var requestBuilder = get("/search/instances")
      .queryParam("query", cqlQuery)
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(exceptionMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("SearchServiceException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")));
  }

  @Test
  void search_negative_unsupportedCqlQueryModifier() throws Exception {
    var cqlQuery = "title all \"test-query\" and";
    var expectedSearchRequest = searchServiceRequest(Instance.class, cqlQuery);
    var exceptionMessage = "Failed to parse CQL query. Comparator 'within' is not supported.";
    when(searchService.search(expectedSearchRequest)).thenThrow(
      new UnsupportedOperationException(exceptionMessage));

    var requestBuilder = get("/search/instances")
      .queryParam("query", cqlQuery)
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(exceptionMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("UnsupportedOperationException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")));
  }

  @Test
  void getFacets_positive() throws Exception {
    var cqlQuery = "title all \"test-query\"";
    var expectedFacetRequest = facetServiceRequest(INSTANCE_RESOURCE, cqlQuery, "source:5");
    when(facetService.getFacets(expectedFacetRequest)).thenReturn(
      facetResult(mapOf("source", facet(List.of(facetItem("MARC", 20), facetItem("FOLIO", 10))))));

    var requestBuilder = get("/search/instances/facets")
      .queryParam("query", cqlQuery)
      .queryParam("facet", "source:5")
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.facets.source.totalRecords", is(2)))
      .andExpect(jsonPath("$.facets.source.values[0].id", is("MARC")))
      .andExpect(jsonPath("$.facets.source.values[0].totalRecords", is(20)))
      .andExpect(jsonPath("$.facets.source.values[1].id", is("FOLIO")))
      .andExpect(jsonPath("$.facets.source.values[1].totalRecords", is(10)));
  }

  @Test
  void getFacets_negative_unknownFacet() throws Exception {
    var cqlQuery = "title all \"test-query\"";
    var expectedFacetRequest = facetServiceRequest(INSTANCE_RESOURCE, cqlQuery, "source:5");
    when(facetService.getFacets(expectedFacetRequest)).thenThrow(
      new RequestValidationException("Invalid facet value", "facet", "source"));

    var requestBuilder = get("/search/instances/facets")
      .queryParam("query", cqlQuery)
      .queryParam("facet", "source:5")
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Invalid facet value")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("facet")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("source")));
  }

  @Test
  void getInstanceIds_positive() throws Exception {
    var cqlQuery = "id=*";
    var instanceId = randomId();
    var request = CqlResourceIdsRequest.of(INSTANCE_RESOURCE, TENANT_ID, cqlQuery, ID_FIELD);

    doAnswer(inv -> {
      var out = (OutputStream) inv.getArgument(1);
      var resourceIds = new ResourceIds().totalRecords(1).ids(List.of(new ResourceId().id(instanceId)));
      out.write(OBJECT_MAPPER.writeValueAsBytes(resourceIds));
      return null;
    }).when(resourceIdService).streamResourceIds(eq(request), any(OutputStream.class));

    var requestBuilder = get("/search/instances/ids")
      .queryParam("query", cqlQuery)
      .contentType(APPLICATION_JSON)
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.ids[0].id", is(instanceId)));
  }
}
