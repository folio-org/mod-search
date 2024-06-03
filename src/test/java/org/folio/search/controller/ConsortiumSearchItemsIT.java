package org.folio.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.controller.SearchConsortiumController.REQUEST_NOT_ALLOWED_MSG;
import static org.folio.search.model.Pair.pair;
import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.sample.SampleInstances.getSemanticWebId;
import static org.folio.search.support.base.ApiEndpoints.consortiumBatchItemsSearchPath;
import static org.folio.search.support.base.ApiEndpoints.consortiumItemSearchPath;
import static org.folio.search.support.base.ApiEndpoints.consortiumItemsSearchPath;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.search.domain.dto.BatchIdsDto;
import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.search.domain.dto.ConsortiumItemCollection;
import org.folio.search.model.Pair;
import org.folio.search.support.base.BaseConsortiumIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class ConsortiumSearchItemsIT extends BaseConsortiumIntegrationTest {

  static final String WRONG_SIZE_MSG =
    "size must be between 0 and 1024";

  @BeforeAll
  static void prepare() {
    setUpTenant(CENTRAL_TENANT_ID);
    setUpTenant(MEMBER_TENANT_ID, getSemanticWeb());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void doGetConsortiumItems_returns200AndRecords() {
    List<Pair<String, String>> queryParams = List.of(
      pair("instanceId", getSemanticWebId())
    );
    var result = doGet(consortiumItemsSearchPath(queryParams), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumItemCollection.class);

    assertThat(actual.getTotalRecords()).isEqualTo(3);
    assertThat(actual.getItems()).containsExactlyInAnyOrder(getExpectedItems());
  }

  @Test
  void doGetConsortiumItems_returns200AndRecords_withAllQueryParams() {
    List<Pair<String, String>> queryParams = List.of(
      pair("instanceId", getSemanticWebId()),
      pair("tenantId", MEMBER_TENANT_ID),
      pair("holdingsRecordId", "e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19"),
      pair("limit", "1"),
      pair("offset", "1"),
      pair("sortBy", "barcode"),
      pair("sortOrder", "desc")
    );
    var result = doGet(consortiumItemsSearchPath(queryParams), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumItemCollection.class);

    assertThat(actual.getTotalRecords()).isEqualTo(2);
    assertThat(actual.getItems())
      .satisfiesExactly(input -> assertEquals("10101", input.getBarcode()));
  }

  @Test
  void tryGetConsortiumItems_returns400_whenRequestedForNotCentralTenant() throws Exception {
    tryGet(consortiumItemsSearchPath())
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is(REQUEST_NOT_ALLOWED_MSG)))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("x-okapi-tenant")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(MEMBER_TENANT_ID)));
  }

  @Test
  void tryGetConsortiumItems_returns400_whenInstanceIdIsNotSpecified() throws Exception {
    List<Pair<String, String>> queryParams = List.of(
      pair("limit", "1"),
      pair("offset", "1"),
      pair("sortBy", "barcode"),
      pair("sortOrder", "desc")
    );
    tryGet(consortiumItemsSearchPath(queryParams), CENTRAL_TENANT_ID)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is("instanceId filter is required")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void doGetConsortiumItem_returns200AndRecords() {
    var items = getExpectedItems();
    var expectedItem = items[0];
    var result = doGet(consortiumItemSearchPath(expectedItem.getId()), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, ConsortiumItem.class);

    assertThat(actual)
      .isEqualTo(expectedItem);
  }

  @Test
  void tryGetConsortiumItem_returns400_whenRequestedForNotCentralTenant() throws Exception {
    tryGet(consortiumItemSearchPath(UUID.randomUUID().toString()))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is(REQUEST_NOT_ALLOWED_MSG)))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("x-okapi-tenant")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(MEMBER_TENANT_ID)));
  }

  @Test
  void doGetConsortiumBatchItems_returns200AndRecords() {
    var items = getExpectedItems();
    var request = new BatchIdsDto()
      .ids(Arrays.stream(items).map(ConsortiumItem::getId).map(UUID::fromString).toList());
    var result = doPost(consortiumBatchItemsSearchPath(), CENTRAL_TENANT_ID, request);
    var actual = parseResponse(result, ConsortiumItemCollection.class);

    assertThat(actual.getTotalRecords()).isEqualTo(3);
    assertThat(actual.getItems()).containsExactlyInAnyOrder(items);
  }

  @Test
  void tryGetConsortiumBatchItems_returns400_whenRequestedForNotCentralTenant() throws Exception {
    tryPost(consortiumBatchItemsSearchPath(), new BatchIdsDto().ids(List.of(UUID.randomUUID())))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is(REQUEST_NOT_ALLOWED_MSG)))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("x-okapi-tenant")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(MEMBER_TENANT_ID)));
  }

  @Test
  void tryGetConsortiumBatchItems_returns400_whenMoreIdsThanLimit() throws Exception {
    var request = new BatchIdsDto()
      .ids(
        Stream.iterate(0, i -> i < 1025, i -> ++i)
          .map(i -> UUID.randomUUID())
          .toList()
      );

    tryPost(consortiumBatchItemsSearchPath(), request)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is(WRONG_SIZE_MSG)))
      .andExpect(jsonPath("$.errors[0].type", is("MethodArgumentNotValidException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("ids")));
  }

  private ConsortiumItem[] getExpectedItems() {
    var instance = getSemanticWeb();
    return instance.getItems().stream()
      .map(item -> new ConsortiumItem()
        .id(item.getId())
        .hrid(item.getHrid())
        .tenantId(MEMBER_TENANT_ID)
        .instanceId(instance.getId())
        .holdingsRecordId(item.getHoldingsRecordId())
        .barcode(item.getBarcode())
      )
      .toArray(ConsortiumItem[]::new);
  }
}
