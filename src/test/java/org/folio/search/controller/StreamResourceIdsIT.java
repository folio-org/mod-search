package org.folio.search.controller;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.awaitility.Awaitility.await;
import static org.folio.search.sample.SampleAuthorities.getAuthoritySampleAsMap;
import static org.folio.search.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.search.support.base.ApiEndpoints.resourcesIdsJobPath;
import static org.folio.search.support.base.ApiEndpoints.resourcesIdsPath;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.awaitility.Durations;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.domain.dto.ResourceIdsJob.EntityTypeEnum;
import org.folio.search.support.base.ApiEndpoints;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.test.type.IntegrationTest;
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

  @MethodSource("testDataProvider")
  @ParameterizedTest
  @DisplayName("init resource IDs job and stream all resources IDs")
  void createIdsJobAndStreamIds(EntityTypeEnum entityType, List<String> expectedIds) throws Exception {
    var query = "cql.allRecords=1";
    var resourceIdsJob = new ResourceIdsJob().query(query).entityType(entityType);
    var postResponse = parseResponse(doPost(ApiEndpoints.resourcesIdsJobPath(), resourceIdsJob)
      .andExpect(jsonPath("$.query", is(query)))
      .andExpect(jsonPath("$.entityType", is(entityType.name())))
      .andExpect(jsonPath("$.id", anything())), ResourceIdsJob.class);

    await().atMost(Durations.FIVE_SECONDS).until(() -> {
      var response = doGet(resourcesIdsJobPath(postResponse.getId()));
      return parseResponse(response, ResourceIdsJob.class).getStatus().equals(ResourceIdsJob.StatusEnum.COMPLETED);
    });

    doGet(resourcesIdsPath(postResponse.getId()))
      .andExpect(jsonPath("totalRecords", is(expectedIds.size())))
      .andExpect(jsonPath("ids[*].id", containsInAnyOrder(expectedIds.toArray())));
  }

  @Test
  void createIdsJobAndStreamIdsForLargeQuery() throws Exception {
    var query = Stream.generate(UUID::randomUUID)
      .map(UUID::toString)
      .limit(7)
      .collect(Collectors.joining(" or id=", "id=", EMPTY)); //297 query length
    var resourceIdsJob = new ResourceIdsJob().query(query).entityType(EntityTypeEnum.INSTANCE);
    var postResponse = parseResponse(doPost(ApiEndpoints.resourcesIdsJobPath(), resourceIdsJob)
      .andExpect(jsonPath("$.query", is(query)))
      .andExpect(jsonPath("$.entityType", is(EntityTypeEnum.INSTANCE.name())))
      .andExpect(jsonPath("$.id", anything())), ResourceIdsJob.class);

    await().atMost(Durations.FIVE_SECONDS).until(() -> {
      var response = doGet(resourcesIdsJobPath(postResponse.getId()));
      return parseResponse(response, ResourceIdsJob.class).getStatus().equals(ResourceIdsJob.StatusEnum.COMPLETED);
    });
  }

  @Test
  void cantStreamDeprecatedJob() throws Exception {
    var query = "cql.allRecords=1";
    var postResponse = parseResponse(doPost(ApiEndpoints.resourcesIdsJobPath(), new ResourceIdsJob()
      .query(query)
      .entityType(EntityTypeEnum.AUTHORITY))
      .andExpect(jsonPath("$.id", anything())), ResourceIdsJob.class);

    await().atMost(Durations.FIVE_SECONDS).until(() -> {
      var response = doGet(resourcesIdsJobPath(postResponse.getId()));
      return parseResponse(response, ResourceIdsJob.class).getStatus().equals(ResourceIdsJob.StatusEnum.COMPLETED);
    });

    doGet(resourcesIdsPath(postResponse.getId()));

    attemptGet(resourcesIdsPath(postResponse.getId()))
      .andExpect(status().is4xxClientError());
  }

  @Test
  void cantStreamWithInvalidQuery() throws Exception {
    var query = "bad query";
    var postResponse = parseResponse(doPost(ApiEndpoints.resourcesIdsJobPath(), new ResourceIdsJob()
      .query(query)
      .entityType(EntityTypeEnum.INSTANCE))
      .andExpect(jsonPath("$.id", anything())), ResourceIdsJob.class);

    await().atMost(Durations.FIVE_SECONDS).until(() -> {
      var response = doGet(resourcesIdsJobPath(postResponse.getId()));
      return parseResponse(response, ResourceIdsJob.class).getStatus().equals(ResourceIdsJob.StatusEnum.ERROR);
    });
  }

  @Test
  void cantGetJobWithInvalidId() throws Exception {
    attemptGet(resourcesIdsPath("randomUUID"))
      .andExpect(status().is4xxClientError());
  }

  public static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments(EntityTypeEnum.INSTANCE, List.of("5bf370e0-8cca-4d9c-82e4-5170ab2a0a39")),
      arguments(EntityTypeEnum.AUTHORITY, List.of("55294032-fcf6-45cc-b6da-4420a61ef72c")),
      arguments(EntityTypeEnum.HOLDINGS, List.of("a663dea9-6547-4b2d-9daa-76cadd662272",
        "9550c935-401a-4a85-875e-4d1fe7678870",
        "e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19"))
    );
  }

}
