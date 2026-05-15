package org.folio.indexing;

import static org.assertj.core.api.AssertionsForClassTypes.entry;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.folio.search.model.types.ResourceType.INSTANCE_CLASSIFICATION;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.utils.TestUtils.randomId;

import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.Classification;
import org.folio.search.domain.dto.Instance;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.Test;

public abstract class IndexingInstanceClassificationIT extends BaseSharedTest {

  @Test
  void shouldIndexInstanceClassification_createNewDocument() {
    var instanceId1 = randomId();
    var instanceId2 = randomId();
    var typeId = "ce176ace-a53e-4b4d-aa89-725ed7b2edac";
    var number = "N123";
    var classification = new Classification().classificationNumber(number).classificationTypeId(typeId);
    var instance1 = new Instance().id(instanceId1).addClassificationsItem(classification);
    var instance2 = new Instance().id(instanceId2).addClassificationsItem(classification);
    inventoryApi.createInstance(TENANT_ID, instance1);
    inventoryApi.createInstance(TENANT_ID, instance2);
    assertCountByIds(instanceSearchPath(), List.of(instanceId1, instanceId2), 2);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CLASSIFICATION, TENANT_ID))
      .as("Should have exactly 1 classification document in the index")
      .hasSize(1));

    var hits = fetchAllDocuments(INSTANCE_CLASSIFICATION, TENANT_ID);
    var sourceAsMap = hits[0].getSourceAsMap();
    assertClassificationDocFields(sourceAsMap, number, typeId);
    assertClassificationInstancesGroup(sourceAsMap);
  }

  @Test
  void shouldIndexInstanceClassification_deleteDocumentOnInstanceUpdate() {
    var instanceId = randomId();
    var classification = new Classification().classificationNumber("N123").classificationTypeId("type1");
    var instance = new Instance().id(instanceId).addClassificationsItem(classification);
    inventoryApi.createInstance(TENANT_ID, instance);
    assertCountByIds(instanceSearchPath(), List.of(instanceId), 1);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CLASSIFICATION, TENANT_ID))
      .as("Should have exactly 1 classification document before instance update")
      .hasSize(1));
    inventoryApi.updateInstance(TENANT_ID, instance.classifications(null));
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CLASSIFICATION, TENANT_ID))
      .as("Classification document should be removed after instance classifications cleared")
      .isEmpty());
  }

  @Test
  void shouldIndexInstanceClassification_deleteDocumentOnInstanceDelete() {
    var instanceId = randomId();
    var classification = new Classification().classificationNumber("N123").classificationTypeId("type1");
    var instance = new Instance().id(instanceId).addClassificationsItem(classification);
    inventoryApi.createInstance(TENANT_ID, instance);
    assertCountByIds(instanceSearchPath(), List.of(instanceId), 1);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CLASSIFICATION, TENANT_ID))
      .as("Should have exactly 1 classification document before instance delete")
      .hasSize(1));
    inventoryApi.deleteInstance(TENANT_ID, instanceId);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CLASSIFICATION, TENANT_ID))
      .as("Classification document should be removed after instance delete")
      .isEmpty());
  }

  @SuppressWarnings("unchecked")
  private void assertClassificationInstancesGroup(Map<String, Object> sourceAsMap) {
    var instances = (List<Map<String, Object>>) sourceAsMap.get("instances");
    assertThat(instances)
      .as("Instances list should contain exactly 1 group with count 2")
      .hasSize(1)
      .allSatisfy(map -> assertThat(map).containsEntry("shared", false))
      .allSatisfy(map -> assertThat(map).containsEntry("tenantId", TENANT_ID))
      .allSatisfy(map -> assertThat(map).containsEntry("count", 2));
  }

  private void assertClassificationDocFields(Map<String, Object> sourceAsMap, String number, String lcTypeId) {
    assertThat(sourceAsMap)
      .as("Classification document should contain expected indexed fields")
      .contains(
        entry("number", number),
        entry("typeId", lcTypeId),
        entry("defaultShelvingOrder", "N123"),
        entry("deweyShelvingOrder", "N 3123"),
        entry("lcShelvingOrder", "N 3123")
      );
  }
}
