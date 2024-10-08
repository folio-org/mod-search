package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.folio.search.model.types.ResourceType.AUTHORITY;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.model.types.ResourceType.INSTANCE_SUBJECT;
import static org.folio.search.support.base.ApiEndpoints.authorityBrowsePath;
import static org.folio.search.support.base.ApiEndpoints.instanceCallNumberBrowsePath;
import static org.folio.search.support.base.ApiEndpoints.instanceSubjectBrowsePath;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_BROWSING_FIELD;
import static org.folio.search.utils.SearchUtils.SHELVING_ORDER_BROWSING_FIELD;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.authorityBrowseItem;
import static org.folio.search.utils.TestUtils.subjectBrowseItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.Authority;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.service.BrowseRequest;
import org.folio.search.service.browse.AuthorityBrowseService;
import org.folio.search.service.browse.CallNumberBrowseService;
import org.folio.search.service.browse.ClassificationBrowseService;
import org.folio.search.service.browse.ContributorBrowseService;
import org.folio.search.service.browse.SubjectBrowseService;
import org.folio.search.service.consortium.TenantProvider;
import org.folio.search.service.setter.SearchResponsePostProcessor;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@WebMvcTest(BrowseController.class)
@Import({ApiExceptionHandler.class})
class BrowseControllerTest {

  @Autowired
  private MockMvc mockMvc;
  @MockBean
  private SubjectBrowseService subjectBrowseService;
  @MockBean
  private AuthorityBrowseService authorityBrowseService;
  @MockBean
  private CallNumberBrowseService callNumberBrowseService;
  @MockBean
  private ContributorBrowseService contributorBrowseService;
  @MockBean
  private ClassificationBrowseService classificationBrowseService;
  @MockBean
  private TenantProvider tenantProvider;
  @Mock
  private Map<Class<?>, SearchResponsePostProcessor<?>> searchResponsePostProcessors = Collections.emptyMap();

  @BeforeEach
  public void setUp() {
    lenient().when(tenantProvider.getTenant(TENANT_ID))
      .thenReturn(TENANT_ID);
  }

  @Test
  void browseInstancesByCallNumber_positive() throws Exception {
    var query = "callNumber > PR4034 .P7 2019";
    var request = browseRequest(query);
    when(callNumberBrowseService.browse(request)).thenReturn(BrowseResult.empty());
    var requestBuilder = get(instanceCallNumberBrowsePath())
      .queryParam("query", query)
      .queryParam("limit", "5")
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.items", is(emptyList())));
  }

  @Test
  void browseInstancesByCallNumber_positive_allFields() throws Exception {
    var query = "callNumber > B";
    var request = BrowseRequest.of(INSTANCE, TENANT_ID,
      query, 20, SHELVING_ORDER_BROWSING_FIELD, CALL_NUMBER_BROWSING_FIELD, true, true, 5);
    when(callNumberBrowseService.browse(request)).thenReturn(BrowseResult.empty());

    var requestBuilder = get(instanceCallNumberBrowsePath())
      .queryParam("query", query)
      .queryParam("limit", "20")
      .queryParam("expandAll", "true")
      .queryParam("highlightMatch", "true")
      .queryParam("precedingRecordsCount", "5")
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.items", is(emptyList())));
  }

  @Test
  void browseInstancesBySubject_positive() throws Exception {
    var query = "value > water";
    var request = BrowseRequest.of(INSTANCE_SUBJECT, TENANT_ID, query, 25, "value", null, null, true, 12);
    var browseResult = BrowseResult.of(1, List.of(subjectBrowseItem(10, "water treatment")));
    when(subjectBrowseService.browse(request)).thenReturn(browseResult);
    var requestBuilder = get(instanceSubjectBrowsePath())
      .queryParam("query", query)
      .queryParam("limit", "25")
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.items[0].value", is("water treatment")))
      .andExpect(jsonPath("$.items[0].totalRecords", is(10)));
  }

  @Test
  void browseAuthoritiesByHeadingRef_positive() throws Exception {
    var query = "headingRef > mark";
    var request = BrowseRequest.of(AUTHORITY, TENANT_ID, query, 25, "headingRef", null, false, true, 12);
    var authority = new Authority().id(RESOURCE_ID).headingRef("mark twain");
    var browseResult = BrowseResult.of(1, List.of(authorityBrowseItem("mark twain", authority)));
    when(authorityBrowseService.browse(request)).thenReturn(browseResult);
    var requestBuilder = get(authorityBrowsePath())
      .queryParam("query", query)
      .queryParam("limit", "25")
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.items[0].headingRef", is("mark twain")))
      .andExpect(jsonPath("$.items[0].authority.id", is(RESOURCE_ID)))
      .andExpect(jsonPath("$.items[0].authority.headingRef", is("mark twain")));
  }

  @Test
  void browseInstancesByCallNumber_negative_missingQueryParameter() throws Exception {
    var requestBuilder = get(instanceCallNumberBrowsePath())
      .queryParam("limit", "5")
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    var expectedErrorMessage = "Required request parameter 'query' for method parameter type String is not present";
    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(expectedErrorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("MissingServletRequestParameterException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void browseInstancesByCallNumber_negative_precedingRecordsMoreThatLimit() throws Exception {
    var requestBuilder = get(instanceCallNumberBrowsePath())
      .queryParam("query", "callNumber > A")
      .queryParam("limit", "5")
      .queryParam("precedingRecordsCount", "10")
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Preceding records count must be less than request limit")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("precedingRecordsCount")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("10")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void browseInstancesByCallNumber_negative_precedingRecordsCountIsZero() throws Exception {
    var requestBuilder = get(instanceCallNumberBrowsePath())
      .queryParam("query", "callNumber >= A or callNumber < A")
      .queryParam("limit", "5")
      .queryParam("precedingRecordsCount", "0")
      .contentType(APPLICATION_JSON)
      .header(XOkapiHeaders.TENANT, TENANT_ID);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].type", is("ConstraintViolationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].message", is(
        "browseInstancesByCallNumber.precedingRecordsCount must be greater than or equal to 1")));
  }

  private BrowseRequest browseRequest(String query) {
    return BrowseRequest.of(INSTANCE, TENANT_ID, query, 5,
      SHELVING_ORDER_BROWSING_FIELD, CALL_NUMBER_BROWSING_FIELD, false, true, 5 / 2);
  }
}
