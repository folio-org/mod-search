package org.folio.search.controller;

import static java.util.stream.Collectors.toList;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Duration.ONE_MINUTE;
import static org.folio.search.client.cql.CqlQuery.exactMatchAny;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.search.support.base.ApiEndpoints.authoritySearchPath;
import static org.folio.search.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.getResourceName;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestConstants.inventoryAuthorityTopic;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.toMap;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.IntStream;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.support.base.ApiEndpoints;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.folio.spring.integration.XOkapiHeaders;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@IntegrationTest
class IndexingIT extends BaseIntegrationTest {

  private static final List<String> INSTANCE_IDS = getRandomIds(3);
  private static final List<String> ITEM_IDS = getRandomIds(2);
  private static final List<String> HOLDING_IDS = getRandomIds(4);

  @BeforeAll
  static void prepare() {
    setUpTenant(Instance.class, getSemanticWebAsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void shouldRemoveItem() {
    createInstances();
    var itemIdToDelete = ITEM_IDS.get(1);
    inventoryApi.deleteItem(TENANT_ID, itemIdToDelete);
    assertCountByQuery(instanceSearchPath(), "items.id=={value}", itemIdToDelete, 0);
    assertCountByQuery(instanceSearchPath(), "items.id=={value}", ITEM_IDS.get(0), 1);
  }

  @Test
  void shouldRemoveHolding() {
    createInstances();
    inventoryApi.deleteHolding(TENANT_ID, HOLDING_IDS.get(0));
    assertCountByQuery(instanceSearchPath(), "holdings.id=={value}", HOLDING_IDS.get(0), 0);
    HOLDING_IDS.subList(1, 4).forEach(id -> assertCountByQuery(instanceSearchPath(), "holdings.id=={value}", id, 1));
  }

  @Test
  void shouldRemoveInstance() {
    createInstances();
    var instanceIdToDelete = INSTANCE_IDS.get(0);
    inventoryApi.deleteInstance(TENANT_ID, instanceIdToDelete);
    assertCountByQuery(instanceSearchPath(), "id=={value}", instanceIdToDelete, 0);
    INSTANCE_IDS.subList(1, 3).forEach(id -> assertCountByQuery(instanceSearchPath(), "id=={value}", id, 1));
  }

  @Test
  void shouldRemoveAuthority() {
    var authorityId = randomId();
    var authority = new Authority().id(authorityId).personalName("personal name")
      .corporateName("corporate name").uniformTitle("uniform title");
    var resourceEvent = resourceEvent(authorityId, AUTHORITY_RESOURCE, toMap(authority));
    kafkaTemplate.send(inventoryAuthorityTopic(TENANT_ID), resourceEvent);
    assertCountByQuery(authoritySearchPath(), "id=={value}", authorityId, 3);

    var deleteEvent = resourceEvent(authorityId, AUTHORITY_RESOURCE, null).type(DELETE).old(toMap(authority));
    kafkaTemplate.send(inventoryAuthorityTopic(TENANT_ID), deleteEvent);
    assertCountByQuery(authoritySearchPath(), "id=={value}", authority, 0);
  }

  @Test
  void runReindex_positive_instance() throws Exception {
    var request = post(ApiEndpoints.reindexPath())
      .headers(defaultHeaders())
      .header(XOkapiHeaders.URL, okapi.getOkapiUrl())
      .contentType(MediaType.APPLICATION_JSON);

    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is("77ef33c0-2774-45e9-9f45-eb54082e2820")))
      .andExpect(jsonPath("$.jobStatus", is("In progress")))
      .andExpect(jsonPath("$.submittedDate", is("2021-11-08T12:00:00.000+00:00")));
  }

  @Test
  void runReindex_positive_authority() throws Exception {
    var request = post(ApiEndpoints.reindexPath())
      .content(asJsonString(new ReindexRequest().resourceName(getResourceName(Authority.class))))
      .headers(defaultHeaders())
      .header(XOkapiHeaders.URL, okapi.getOkapiUrl())
      .contentType(MediaType.APPLICATION_JSON);

    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is("37bd1461-ee1a-4522-9f8c-93bab186fad3")))
      .andExpect(jsonPath("$.jobStatus", is("In progress")))
      .andExpect(jsonPath("$.submittedDate", is("2021-11-08T13:00:00.000+00:00")));
  }

  @Test
  void runReindex_positive_instanceSubject() throws Exception {
    var request = post(ApiEndpoints.reindexPath())
      .content(asJsonString(new ReindexRequest().resourceName("instance_subject")))
      .headers(defaultHeaders())
      .header(XOkapiHeaders.URL, okapi.getOkapiUrl())
      .contentType(MediaType.APPLICATION_JSON);

    mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Reindex request contains invalid resource name")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("resourceName")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("instance_subject")));
  }

  private void createInstances() {
    var instances = INSTANCE_IDS.stream()
      .map(id -> new Instance().id(id))
      .collect(toList());

    instances.get(0)
      .holdings(List.of(holding(0), holding(1)))
      .items(List.of(item(0), item(1)));

    instances.get(1).holdings(List.of(holding(2), holding(3)));

    instances.forEach(instance -> inventoryApi.createInstance(TENANT_ID, instance));
    assertCountByQuery(instanceSearchPath(), "{value}", exactMatchAny("id", INSTANCE_IDS), 3);
  }

  private static Item item(int i) {
    return new Item().id(ITEM_IDS.get(i));
  }

  private static Holding holding(int i) {
    return new Holding().id(HOLDING_IDS.get(i));
  }

  private static void assertCountByQuery(String path, String query, Object value, int expectedCount) {
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      doSearch(path, prepareQuery(query, String.valueOf(value)))
        .andExpect(jsonPath("totalRecords", is(expectedCount))));
  }

  private static List<String> getRandomIds(int count) {
    return IntStream.range(0, count).mapToObj(index -> randomId()).collect(toList());
  }
}
