package org.folio.search.controller;

import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.model.client.CqlQuery.exactMatchAny;
import static org.folio.search.model.client.CqlQueryParam.HOLDINGS_ID;
import static org.folio.search.model.client.CqlQueryParam.ID;
import static org.folio.search.support.base.ApiEndpoints.authoritySearchPath;
import static org.folio.search.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.SearchUtils.getResourceName;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestConstants.inventoryAuthorityTopic;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.toMap;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.IndexDynamicSettings;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.domain.dto.Subject;
import org.folio.search.domain.dto.UpdateIndexDynamicSettingsRequest;
import org.folio.search.model.client.CqlQueryParam;
import org.folio.search.support.base.ApiEndpoints;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.test.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.action.get.GetRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@IntegrationTest
class RecordsIndexingIT extends BaseIntegrationTest {

  private static final List<String> INSTANCE_IDS = getRandomIds(3);
  private static final List<String> ITEM_IDS = getRandomIds(2);
  private static final List<String> HOLDING_IDS = getRandomIds(4);

  @Autowired
  private RestHighLevelClient restHighLevelClient;

  @BeforeAll
  static void prepare() {
    setUpTenant(Instance.class);
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
  void shouldUpdateAndDeleteInstance() {
    var instanceId = randomId();
    var instance = new Instance().id(instanceId).title("test-resource").subjects(List.of(new Subject().value("s1"),
      new Subject().value("s2")));

    inventoryApi.createInstance(TENANT_ID, instance);
    assertCountByQuery(instanceSearchPath(), "subjects=={value}", "(s1 and s2)", 1);
    assertSubjectExistenceById(sha1Hex(TENANT_ID + "|s1|null"), true);
    assertSubjectExistenceById(sha1Hex(TENANT_ID + "|s2|null"), true);

    var instanceToUpdate = new Instance().id(instanceId).title("test-resource").subjects(
      List.of(new Subject().value("s2"), new Subject().value("s3")));
    inventoryApi.updateInstance(TENANT_ID, instanceToUpdate);
    assertCountByQuery(instanceSearchPath(), "subjects=={value}", "(s2 and s3)", 1);
    assertSubjectExistenceById(sha1Hex(TENANT_ID + "|s1|null"), false);
    assertSubjectExistenceById(sha1Hex(TENANT_ID + "|s3|null"), true);

    inventoryApi.deleteInstance(TENANT_ID, instanceId);
    assertCountByQuery(instanceSearchPath(), "id=={value}", instanceId, 0);
    assertSubjectExistenceById(sha1Hex(TENANT_ID + "|s2|null"), false);
    assertSubjectExistenceById(sha1Hex(TENANT_ID + "|s3|null"), false);
  }

  @Test
  void shouldRemoveHolding() {
    createInstances();
    inventoryApi.deleteHolding(TENANT_ID, HOLDING_IDS.get(0));
    assertCountByQuery(instanceSearchPath(), HOLDINGS_ID, List.of(HOLDING_IDS.get(0)), 0);
    HOLDING_IDS.subList(1, 4).forEach(id -> assertCountByQuery(instanceSearchPath(), HOLDINGS_ID, List.of(id), 1));
  }

  @Test
  void shouldRemoveInstance() throws IOException {
    createInstances();
    var instanceIdToDelete = INSTANCE_IDS.get(0);

    assertThat(isInstanceSubjectExistsById(getSubjectId(instanceIdToDelete))).isTrue();

    inventoryApi.deleteInstance(TENANT_ID, instanceIdToDelete);
    assertCountByQuery(instanceSearchPath(), ID, List.of(instanceIdToDelete), 0);
    assertCountByQuery(instanceSearchPath(), ID, INSTANCE_IDS.subList(1, 3), 2);

    await(() -> assertThat(isInstanceSubjectExistsById(getSubjectId(instanceIdToDelete))).isFalse());
  }

  @Test
  void shouldRemoveAuthority() {
    var authorityId = randomId();
    var authority = new Authority().id(authorityId).personalName("personal name")
      .corporateName("corporate name").uniformTitle("uniform title");
    var resourceEvent = resourceEvent(authorityId, AUTHORITY_RESOURCE, toMap(authority));
    kafkaTemplate.send(inventoryAuthorityTopic(TENANT_ID), resourceEvent);
    assertCountByQuery(authoritySearchPath(), ID, List.of(authorityId), 3);

    var deleteEvent = resourceEvent(authorityId, AUTHORITY_RESOURCE, null).type(DELETE).old(toMap(authority));
    kafkaTemplate.send(inventoryAuthorityTopic(TENANT_ID), deleteEvent);
    assertCountByQuery(authoritySearchPath(), ID, List.of(authorityId), 0);
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

  @Test
  void runReindex_positive_contributor() throws Exception {
    var request = post(ApiEndpoints.reindexPath())
      .content(asJsonString(new ReindexRequest().resourceName("contributor")))
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
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("contributor")));
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
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("invalid-resource")));
  }

  private static Item item(int i) {
    return new Item().id(ITEM_IDS.get(i));
  }

  private static Holding holdingsRecord(int i) {
    return new Holding().id(HOLDING_IDS.get(i));
  }

  private static void assertCountByQuery(String path, CqlQueryParam param, List<String> ids, int expected) {
    var query = exactMatchAny(param, ids).toString();
    await(() -> doSearch(path, query).andExpect(jsonPath("$.totalRecords", is(expected))));
  }

  private static void assertCountByQuery(String path, String template, String value, int expected) {
    await(() -> doSearch(path, prepareQuery(template, value)).andExpect(jsonPath("$.totalRecords", is(expected))));
  }

  private static void await(ThrowingRunnable runnable) {
    Awaitility.await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(runnable);
  }

  private static List<String> getRandomIds(int count) {
    return IntStream.range(0, count).mapToObj(index -> randomId()).toList();
  }

  private static String getSubjectId(String instanceId) {
    return sha1Hex(TENANT_ID + "|subject-" + sha1Hex(instanceId) + "|null");
  }

  private void createInstances() {
    var instances = INSTANCE_IDS.stream()
      .map(id -> new Instance().id(id).subjects(List.of(new Subject().value("subject-" + sha1Hex(id)))))
      .toList();

    instances.get(0)
      .holdings(List.of(holdingsRecord(0), holdingsRecord(1)))
      .items(List.of(item(0), item(1)));

    instances.get(1).holdings(List.of(holdingsRecord(2), holdingsRecord(3)));

    instances.forEach(instance -> inventoryApi.createInstance(TENANT_ID, instance));
    assertCountByQuery(instanceSearchPath(), ID, INSTANCE_IDS, 3);
  }

  private void assertSubjectExistenceById(String subjectId, boolean isExists) {
    await(() -> assertThat(isInstanceSubjectExistsById(subjectId)).isEqualTo(isExists));
  }

  private boolean isInstanceSubjectExistsById(String subjectId) throws IOException {
    var indexName = getIndexName(INSTANCE_SUBJECT_RESOURCE, TENANT_ID);
    var request = new GetRequest(indexName, subjectId);
    var documentById = restHighLevelClient.get(request, RequestOptions.DEFAULT);
    return documentById.isExists() && !documentById.isSourceEmpty();
  }
}
