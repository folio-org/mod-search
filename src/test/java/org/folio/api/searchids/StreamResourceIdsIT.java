package org.folio.api.searchids;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.awaitility.Awaitility.await;
import static org.folio.search.domain.dto.ResourceIdsJob.StatusEnum.COMPLETED;
import static org.folio.search.domain.dto.ResourceIdsJob.StatusEnum.ERROR;
import static org.folio.search.domain.dto.ResourceIdsJob.StatusEnum.IN_PROGRESS;
import static org.folio.search.utils.SearchUtils.ALL_RECORDS_QUERY;
import static org.folio.support.base.ApiEndpoints.resourcesIdsJobPath;
import static org.folio.support.base.ApiEndpoints.resourcesIdsPath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
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
import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.domain.dto.ResourceIdsJob.EntityTypeEnum;
import org.folio.support.base.ApiEndpoints;
import org.folio.support.base.BaseSharedTest;
import org.folio.support.testdata.SharedTestDataManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class StreamResourceIdsIT extends BaseSharedTest {

  @MethodSource("testDataProvider")
  @ParameterizedTest
  @DisplayName("init resource IDs job and stream all resources IDs")
  void createIdsJobAndStreamIds(EntityTypeEnum entityType, List<String> expectedIds) throws Exception {
    var resourceIdsJob = new ResourceIdsJob().query(ALL_RECORDS_QUERY).entityType(entityType);
    var postResponse = parseResponse(doPost(ApiEndpoints.resourcesIdsJobPath(), resourceIdsJob)
      .andExpect(jsonPath("$.query", is(ALL_RECORDS_QUERY)))
      .andExpect(jsonPath("$.entityType", is(entityType.name())))
      .andExpect(jsonPath("$.status", is(IN_PROGRESS.getValue())))
      .andExpect(jsonPath("$.id", anything())), ResourceIdsJob.class);

    await().atMost(Durations.FIVE_SECONDS).until(() -> {
      var response = doGet(resourcesIdsJobPath(postResponse.getId()));
      return requireNonNull(parseResponse(response, ResourceIdsJob.class).getStatus()).equals(COMPLETED);
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
      return requireNonNull(parseResponse(response, ResourceIdsJob.class).getStatus()).equals(COMPLETED);
    });
  }

  @Test
  void cantStreamDeprecatedJob() throws Exception {
    var postResponse = parseResponse(doPost(ApiEndpoints.resourcesIdsJobPath(), new ResourceIdsJob()
      .query(ALL_RECORDS_QUERY)
      .entityType(EntityTypeEnum.AUTHORITY))
      .andExpect(jsonPath("$.id", anything())), ResourceIdsJob.class);

    await().atMost(Durations.FIVE_SECONDS).until(() -> {
      var response = doGet(resourcesIdsJobPath(postResponse.getId()));
      return requireNonNull(parseResponse(response, ResourceIdsJob.class).getStatus()).equals(COMPLETED);
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
      return requireNonNull(parseResponse(response, ResourceIdsJob.class).getStatus()).equals(ERROR);
    });
  }

  @Test
  void cantGetJobWithInvalidId() throws Exception {
    attemptGet(resourcesIdsPath("randomUUID"))
      .andExpect(status().is4xxClientError());
  }

  private static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments(EntityTypeEnum.INSTANCE, SharedTestDataManager.instanceIds()),
      arguments(EntityTypeEnum.AUTHORITY, SharedTestDataManager.authorityIds()),
      arguments(EntityTypeEnum.HOLDINGS, SharedTestDataManager.holdingIds())
    );
  }
}
