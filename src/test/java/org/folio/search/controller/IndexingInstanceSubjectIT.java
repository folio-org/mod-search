package org.folio.search.controller;

import static org.assertj.core.api.AssertionsForClassTypes.entry;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.folio.search.model.types.ResourceType.INSTANCE_SUBJECT;
import static org.folio.search.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.randomId;

import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Subject;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class IndexingInstanceSubjectIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant();
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void shouldIndexInstanceSubject_createDocument() {
    var instanceId1 = randomId();
    var instanceId2 = randomId();
    var authorityId = "ce176ace-a53e-4b4d-aa89-725ed7b2edac";
    var value = "Fantasy";
    var subject = new Subject().value(value).authorityId(authorityId);
    var instance1 = new Instance().id(instanceId1).addSubjectsItem(subject);
    var instance2 = new Instance().id(instanceId2).addSubjectsItem(subject);
    inventoryApi.createInstance(TENANT_ID, instance1);
    inventoryApi.createInstance(TENANT_ID, instance2);
    assertCountByIds(instanceSearchPath(), List.of(instanceId1, instanceId2), 2);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_SUBJECT, TENANT_ID)).hasSize(1));

    var hits = fetchAllDocuments(INSTANCE_SUBJECT, TENANT_ID);
    var sourceAsMap = hits[0].getSourceAsMap();
    assertThat(sourceAsMap)
      .contains(
        entry("value", value),
        entry("authorityId", authorityId)
      );

    @SuppressWarnings("unchecked")
    var instances = (List<Map<String, Object>>) sourceAsMap.get("instances");
    assertThat(instances)
      .allSatisfy(map -> assertThat(map).containsEntry("shared", false))
      .allSatisfy(map -> assertThat(map).containsEntry("tenantId", TENANT_ID))
      .anySatisfy(map -> assertThat(map).containsEntry("instanceId", instanceId1))
      .anySatisfy(map -> assertThat(map).containsEntry("instanceId", instanceId2));
  }

  @Test
  void shouldIndexInstanceSubject_deleteDocumentOnInstanceUpdate() {
    var instanceId = randomId();
    var subject = new Subject().value("Sci-Fi").authorityId(null);
    var instance = new Instance().id(instanceId).addSubjectsItem(subject);
    inventoryApi.createInstance(TENANT_ID, instance);
    assertCountByIds(instanceSearchPath(), List.of(instanceId), 1);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_SUBJECT, TENANT_ID)).hasSize(1));
    inventoryApi.updateInstance(TENANT_ID, instance.subjects(null));
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_SUBJECT, TENANT_ID)).isEmpty());
  }

  @Test
  void shouldIndexInstanceSubject_deleteDocumentOnInstanceDelete() {
    var instanceId = randomId();
    var subject = new Subject().value("Sci-Fi").authorityId(null);
    var instance = new Instance().id(instanceId).addSubjectsItem(subject);
    inventoryApi.createInstance(TENANT_ID, instance);
    assertCountByIds(instanceSearchPath(), List.of(instanceId), 1);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_SUBJECT, TENANT_ID)).hasSize(1));
    inventoryApi.deleteInstance(TENANT_ID, instanceId);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_SUBJECT, TENANT_ID)).isEmpty());
  }
}
