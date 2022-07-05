package org.folio.search.controller;

import static java.util.Collections.singletonList;
import static org.awaitility.Awaitility.await;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySampleAsMap;
import static org.folio.search.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.search.support.base.ApiEndpoints.resourcesIds;
import static org.folio.search.support.base.ApiEndpoints.resourcesIdsJob;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;
import org.awaitility.Durations;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@IntegrationTest
class StreamResourceIdsIT extends BaseIntegrationTest {

  private static final int SAVED_INSTANCES_AMOUNT = 1;
  private static final int SAVED_AUTHORITIES_AMOUNT = 30;

  @BeforeAll
  static void prepare() {
    setUpTenant(List.of(
      new TestData(Instance.class, singletonList(getSemanticWebAsMap()), SAVED_INSTANCES_AMOUNT),
      new TestData(Authority.class, singletonList(getAuthoritySampleAsMap()), SAVED_AUTHORITIES_AMOUNT)
    ), TENANT_ID);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  public static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments(ResourceIdsJob.EntityTypeEnum.HOLDINGS, 3),
      arguments(ResourceIdsJob.EntityTypeEnum.AUTHORITY, 1)
    );
  }

  @MethodSource("testDataProvider")
  @ParameterizedTest
  @DisplayName("init resource IDs job and stream all resources IDs")
  void createIdsJobAndStreamIds(ResourceIdsJob.EntityTypeEnum entityType, int expectedIdsAmount) throws Exception {
    var query = "cql.allRecords=1";
    var resourceIdsJob = new ResourceIdsJob().query(query).entityType(entityType);
    var postResponse = parseResponse(doPost(resourcesIdsJob(), resourceIdsJob)
      .andExpect(jsonPath("$.query", is(query)))
      .andExpect(jsonPath("$.entityType", is(entityType.name())))
      .andExpect(jsonPath("$.id", anything())), ResourceIdsJob.class);

    await().atMost(Durations.FIVE_SECONDS).until(() -> {
      var response = doGet(resourcesIdsJob(postResponse.getId()));
      return parseResponse(response, ResourceIdsJob.class).getStatus().equals(ResourceIdsJob.StatusEnum.COMPLETED);
    });

    doGet(resourcesIds(postResponse.getId()))
      .andExpect(jsonPath("ids", hasSize(expectedIdsAmount)))
      .andExpect(jsonPath("totalRecords", is(expectedIdsAmount)));
  }

  @Test
  void cantStreamDeprecatedJob() throws Exception {
    var query = "cql.allRecords=1";
    var postResponse = parseResponse(doPost(resourcesIdsJob(), new ResourceIdsJob()
      .query(query)
      .entityType(ResourceIdsJob.EntityTypeEnum.AUTHORITY))
      .andExpect(jsonPath("$.id", anything())), ResourceIdsJob.class);

    await().atMost(Durations.FIVE_SECONDS).until(() -> {
      var response = doGet(resourcesIdsJob(postResponse.getId()));
      return parseResponse(response, ResourceIdsJob.class).getStatus().equals(ResourceIdsJob.StatusEnum.COMPLETED);
    });

    doGet(resourcesIds(postResponse.getId()));

    attemptGet(resourcesIds(postResponse.getId()))
      .andExpect(status().is4xxClientError());
  }

  @Test
  void cantStreamNotCompletedJob() throws Exception {
    attemptGet(resourcesIds("randomUUID"))
      .andExpect(status().is4xxClientError());
  }

}
