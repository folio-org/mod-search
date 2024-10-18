package org.folio.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.folio.search.domain.dto.ReindexRequest.ResourceNameEnum.AUTHORITY;
import static org.folio.search.model.types.ResourceType.CAMPUS;
import static org.folio.search.model.types.ResourceType.INSTITUTION;
import static org.folio.search.model.types.ResourceType.LIBRARY;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_WORK;
import static org.folio.search.model.types.ResourceType.LOCATION;
import static org.folio.search.utils.SearchUtils.getResourceName;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.IndexDynamicSettings;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.domain.dto.ReindexRequest.ResourceNameEnum;
import org.folio.search.domain.dto.UpdateIndexDynamicSettingsRequest;
import org.folio.search.model.types.ResourceType;
import org.folio.search.support.base.ApiEndpoints;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

@IntegrationTest
class IndexManagementIT extends BaseIntegrationTest {

  public static final String REINDEX_REQUEST = """
    {
      "resourceName": "%s"
    }
    """;
  public static final String REINDEX_UNEXPECTED_RESOURCE = "Unexpected value '%s'";

  @BeforeAll
  static void prepare() {
    setUpTenant(Instance.class);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void runReindex_positive_authority() throws Exception {
    var request = getReindexRequestBuilder(
      asJsonString(new ReindexRequest().resourceName(AUTHORITY)));

    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is("37bd1461-ee1a-4522-9f8c-93bab186fad3")))
      .andExpect(jsonPath("$.jobStatus", is("In progress")))
      .andExpect(jsonPath("$.submittedDate", is("2021-11-08T13:00:00.000+00:00")));
  }

  @Test
  void runReindex_positive_locations() throws Exception {
    var request = getReindexRequestBuilder(
      asJsonString(new ReindexRequest().resourceName(ReindexRequest.ResourceNameEnum.LOCATION)));

    assertThat(countDefaultIndexDocument(LOCATION)).isZero();
    assertThat(countDefaultIndexDocument(CAMPUS)).isZero();
    assertThat(countDefaultIndexDocument(LIBRARY)).isZero();
    assertThat(countDefaultIndexDocument(INSTITUTION)).isZero();

    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", notNullValue()))
      .andExpect(jsonPath("$.jobStatus", is("Completed")))
      .andExpect(jsonPath("$.submittedDate", notNullValue()));

    await().atMost(FIVE_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() -> {
      var countedLocations = countDefaultIndexDocument(LOCATION);
      var countedCampuses = countDefaultIndexDocument(CAMPUS);
      var countedLibraries = countDefaultIndexDocument(LIBRARY);
      var countedInstitutions = countDefaultIndexDocument(INSTITUTION);

      assertThat(countedLocations).isEqualTo(3);
      assertThat(countedCampuses).isEqualTo(2);
      assertThat(countedLibraries).isEqualTo(2);
      assertThat(countedInstitutions).isEqualTo(2);
    });
  }

  @ParameterizedTest
  @EnumSource(value = ResourceType.class, names = {"LINKED_DATA_WORK", "LINKED_DATA_HUB"})
  void runReindex_shouldRecreate_linkedDataResourcesIndexes(ResourceType resourceType) throws Exception {
    var reindexRequest = new ReindexRequest()
      .resourceName(ResourceNameEnum.valueOf(resourceType.name()))
      .recreateIndex(true);
    var request = getReindexRequestBuilder(asJsonString(reindexRequest));
    var indexIdBeforeReindex = getIndexId(resourceType);

    mockMvc.perform(request)
      .andExpect(status().isOk());

    await().atMost(FIVE_SECONDS)
      .pollInterval(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(() -> {
        assertNotEquals(indexIdBeforeReindex, getIndexId(resourceType));
        assertThat(countDefaultIndexDocument(resourceType)).isZero();
      });
  }

  @ParameterizedTest
  @MethodSource("requestBuilderDataProvider")
  void runReindex_shouldNotRecreate_linkedDataResourcesIndexes(RequestBuilder requestBuilder) throws Exception {
    var indexIdBeforeReindex = getIndexId(LINKED_DATA_WORK);

    mockMvc.perform(requestBuilder)
      .andExpect(status().isOk());

    await().atMost(FIVE_SECONDS)
      .pollInterval(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(() -> assertEquals(indexIdBeforeReindex, getIndexId(LINKED_DATA_WORK)));
  }

  @Test
  void runReindex_negative_instanceSubject() throws Exception {
    var resource = "instance_subject";
    var request = getReindexRequestBuilder(reindexRequestJson(resource));

    mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(reindexUnexpectedResource(resource))))
      .andExpect(jsonPath("$.errors[0].type", is("IllegalArgumentException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void runReindex_negative_contributor() throws Exception {
    var resource = "contributor";
    var request = getReindexRequestBuilder(reindexRequestJson(resource));

    mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(reindexUnexpectedResource(resource))))
      .andExpect(jsonPath("$.errors[0].type", is("IllegalArgumentException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void updateIndexDynamicSettings_positive() throws Exception {
    var request = put(ApiEndpoints.updateIndexSettingsPath())
      .content(asJsonString(new UpdateIndexDynamicSettingsRequest()
        .resourceName(getResourceName(Authority.class))
        .indexSettings(new IndexDynamicSettings().numberOfReplicas(1).refreshInterval(1))))
      .headers(defaultHeaders())
      .header(XOkapiHeaders.URL, okapi.getOkapiUrl())
      .contentType(MediaType.APPLICATION_JSON);

    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("success")));
  }

  @Test
  void updateIndexDynamicSettings_negative() throws Exception {
    var request = put(ApiEndpoints.updateIndexSettingsPath())
      .content(asJsonString(new UpdateIndexDynamicSettingsRequest()
        .resourceName("invalid-resource")
        .indexSettings(new IndexDynamicSettings().numberOfReplicas(1).refreshInterval(1))))
      .headers(defaultHeaders())
      .header(XOkapiHeaders.URL, okapi.getOkapiUrl())
      .contentType(MediaType.APPLICATION_JSON);

    mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Index Settings cannot be updated, resource name is invalid.")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("resourceName")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("unknown")));
  }

  private static String reindexRequestJson(String resource) {
    return REINDEX_REQUEST.formatted(resource);
  }

  private static String reindexUnexpectedResource(String resource) {
    return REINDEX_UNEXPECTED_RESOURCE.formatted(resource);
  }

  private static Stream<Arguments> requestBuilderDataProvider() {
    return Stream.of(
      arguments(
        getReindexRequestBuilder(asJsonString(new ReindexRequest().resourceName(ResourceNameEnum.LINKED_DATA_WORK)))
      ),
      arguments(
        post(ApiEndpoints.reindexPath())
          .content(asJsonString(new ReindexRequest()
            .resourceName(ResourceNameEnum.LINKED_DATA_WORK).recreateIndex(true)))
          .headers(defaultHeaders(MEMBER_TENANT_ID))
          .header(XOkapiHeaders.URL, okapi.getOkapiUrl())
          .contentType(MediaType.APPLICATION_JSON)
      ),
      arguments(
        getReindexRequestBuilder(asJsonString(new ReindexRequest().resourceName(AUTHORITY).recreateIndex(true)))
      )
    );
  }

  private static RequestBuilder getReindexRequestBuilder(String content) {
    return post(ApiEndpoints.reindexPath())
      .content(content)
      .headers(defaultHeaders())
      .header(XOkapiHeaders.URL, okapi.getOkapiUrl())
      .contentType(MediaType.APPLICATION_JSON);
  }

}
