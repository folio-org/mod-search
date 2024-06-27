package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.searchResult;
import static org.folio.search.utils.TestUtils.searchServiceRequest;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Bibframe;
import org.folio.search.domain.dto.BibframeAuthority;
import org.folio.search.domain.dto.Instance;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.service.SearchService;
import org.folio.search.service.consortium.TenantProvider;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.OpenSearchException;
import org.opensearch.core.index.Index;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@Import({ApiExceptionHandler.class})
@WebMvcTest(SearchController.class)
class SearchControllerTest {

  @MockBean
  private SearchService searchService;
  @MockBean
  private TenantProvider tenantProvider;
  @Autowired
  private MockMvc mockMvc;

  @BeforeEach
  public void setUp() {
    lenient().when(tenantProvider.getTenant(TENANT_ID))
      .thenReturn(TENANT_ID);
  }

  @ParameterizedTest
  @MethodSource("provideSearchPaths")
  void search_positive(Class<?> requestClass,
                       String searchPass,
                       boolean expandAll,
                       int limit,
                       String jsonDataPath) throws Exception {

    var cqlQuery = "title all \"test-query\"";
    var expectedSearchRequest = searchServiceRequest(requestClass, TENANT_ID, cqlQuery, expandAll, limit);
    when(searchService.search(expectedSearchRequest))
      .thenReturn(searchResult());

    var requestBuilder = get(searchPass)
      .queryParam("query", cqlQuery)
      .queryParam("limit", String.valueOf(limit))
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath(jsonDataPath, is(emptyList())));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "/search/instances",
    "/search/authorities",
    "/search/bibframe",
    "/search/bibframe/authorities",
  })
  void search_offset_limit_10k(String searchPath) throws Exception {

    var cqlQuery = "title all \"test-query\"";

    when(searchService.search(searchServiceRequest(Instance.class, cqlQuery)))
      .thenReturn(searchResult());

    var requestBuilder = get(searchPath)
      .queryParam("query", cqlQuery)
      .queryParam("limit", "100")
      .queryParam("offset", "10000")
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @MethodSource("provideSearchPaths")
  void search_negative_indexNotFound(Class<?> requestClass,
                                     String searchPass,
                                     boolean expandAll,
                                     int limit) throws Exception {
    var cqlQuery = "title all \"test-query\"";
    var openSearchException = new OpenSearchException("Elasticsearch exception ["
      + "type=index_not_found_exception, "
      + "reason=no such index [instance_test-tenant]]");
    openSearchException.setIndex(new Index(INDEX_NAME, randomId()));
    var expectedSearchRequest = searchServiceRequest(requestClass, TENANT_ID, cqlQuery, expandAll, limit);
    when(searchService.search(expectedSearchRequest)).thenThrow(
      new SearchOperationException("error", openSearchException));

    var requestBuilder = get(searchPass)
      .queryParam("query", cqlQuery)
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Index not found: " + INDEX_NAME)))
      .andExpect(jsonPath("$.errors[0].type", is("OpenSearchException")))
      .andExpect(jsonPath("$.errors[0].code", is("elasticsearch_error")));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Instances           , 500 , /search/instances",
    "Authorities         , 500 , /search/authorities",
    "Bibframe            , 100 , /search/bibframe",
    "BibframeAuthorities , 100 , /search/bibframe/authorities",
  })
  void search_negative_invalidLimitParameter(String classMessagePart, int limit,  String searchPass) throws Exception {
    var expectedMessage = String.format("search%s.limit must be less than or equal to %s", classMessagePart, limit);
    var requestBuilder = get(searchPass)
      .queryParam("query", "title all \"test-query\"")
      .queryParam("limit", "100000")
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(expectedMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("ConstraintViolationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @ParameterizedTest
  @MethodSource("provideSearchPaths")
  void search_negative_invalidCqlQuery(Class<?> requestClass,
                                       String searchPass,
                                       boolean expandAll,
                                       int limit) throws Exception {
    var cqlQuery = "title all \"test-query\" and";
    var expectedSearchRequest = searchServiceRequest(requestClass, TENANT_ID, cqlQuery, expandAll, limit);
    var exceptionMessage = String.format("Failed to parse CQL query [query: '%s']", cqlQuery);
    when(searchService.search(expectedSearchRequest)).thenThrow(new SearchServiceException(exceptionMessage));

    var requestBuilder = get(searchPass)
      .queryParam("query", cqlQuery)
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(exceptionMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("SearchServiceException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")));
  }

  @ParameterizedTest
  @MethodSource("provideSearchPaths")
  void search_negative_unsupportedCqlQueryModifier(Class<?> requestClass,
                                                   String searchPass,
                                                   boolean expandAll,
                                                   int limit) throws Exception {
    var cqlQuery = "title all \"test-query\" and";
    var expectedSearchRequest = searchServiceRequest(requestClass, TENANT_ID, cqlQuery, expandAll, limit);
    var exceptionMessage = "Failed to parse CQL query. Comparator 'within' is not supported.";
    when(searchService.search(expectedSearchRequest)).thenThrow(
      new UnsupportedOperationException(exceptionMessage));

    var requestBuilder = get(searchPass)
      .queryParam("query", cqlQuery)
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(exceptionMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("UnsupportedOperationException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")));
  }

  private static Stream<Arguments> provideSearchPaths() {
    return Stream.of(
      Arguments.of(Instance.class, "/search/instances", false, 100, "$.instances"),
      Arguments.of(Authority.class, "/search/authorities", false, 100, "$.authorities"),
      Arguments.of(Bibframe.class, "/search/bibframe", true, 10, "$.content"),
      Arguments.of(BibframeAuthority.class, "/search/bibframe/authorities", true, 10, "$.content")
    );
  }
}
