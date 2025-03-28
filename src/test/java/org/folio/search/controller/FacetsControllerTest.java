package org.folio.search.controller;

import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.utils.TestUtils.defaultFacetServiceRequest;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.facetResult;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.FacetService;
import org.folio.search.service.consortium.TenantProvider;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@Import({ApiExceptionHandler.class})
@WebMvcTest(FacetsController.class)
class FacetsControllerTest {

  @MockitoBean
  private FacetService facetService;
  @MockitoBean
  private TenantProvider tenantProvider;
  @Autowired
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    lenient().when(tenantProvider.getTenant(TENANT_ID))
      .thenReturn(TENANT_ID);
  }

  @MethodSource("facetsTestSource")
  @ParameterizedTest
  void getFacets_positive(String recordType, ResourceType resource) throws Exception {
    var cqlQuery = "source all \"test-query\"";
    var expectedFacetRequest = defaultFacetServiceRequest(resource, cqlQuery, "source:5");
    when(facetService.getFacets(expectedFacetRequest)).thenReturn(
      facetResult(mapOf("source", facet(List.of(facetItem("MARC", 20), facetItem("FOLIO", 10))))));

    var requestBuilder = get("/search/" + recordType + "/facets")
      .queryParam("query", cqlQuery)
      .queryParam("facet", "source:5")
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

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
    var expectedFacetRequest = defaultFacetServiceRequest(ResourceType.INSTANCE, cqlQuery, "source:5");
    when(facetService.getFacets(expectedFacetRequest)).thenThrow(
      new RequestValidationException("Invalid facet value", "facet", "source"));

    var requestBuilder = get("/search/instances/facets")
      .queryParam("query", cqlQuery)
      .queryParam("facet", "source:5")
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

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
  void getFacets_negative_unknownRecordType() throws Exception {
    var cqlQuery = "title all \"test-query\"";

    var requestBuilder = get("/search/unknownType/facets")
      .queryParam("query", cqlQuery)
      .queryParam("facet", "source:5")
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", containsString("Failed to convert value")))
      .andExpect(jsonPath("$.errors[0].type", is("MethodArgumentTypeMismatchException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  public static Stream<Arguments> facetsTestSource() {
    return Stream.of(
      Arguments.arguments("authorities", ResourceType.AUTHORITY),
      Arguments.arguments("instances", ResourceType.INSTANCE),
      Arguments.arguments("contributors", ResourceType.INSTANCE_CONTRIBUTOR),
      Arguments.arguments("subjects", ResourceType.INSTANCE_SUBJECT),
      Arguments.arguments("classifications", ResourceType.INSTANCE_CLASSIFICATION)
    );
  }
}
