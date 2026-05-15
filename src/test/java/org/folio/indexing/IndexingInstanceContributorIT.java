package org.folio.indexing;

import static org.assertj.core.api.AssertionsForClassTypes.entry;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.folio.search.model.types.ResourceType.INSTANCE_CONTRIBUTOR;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.utils.TestUtils.randomId;

import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Instance;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.Test;

public abstract class IndexingInstanceContributorIT extends BaseSharedTest {

  @Test
  void shouldIndexInstanceContributor_createDocument() {
    var instanceId1 = randomId();
    var instanceId2 = randomId();
    var authorityId = "ce176ace-a53e-4b4d-aa89-725ed7b2edac";
    var typeId = "13f6dc91-c2e5-4d18-a6c8-5015974454ef";
    var nameTypeId = "d2775d47-5e1f-4659-99ff-ccbaff84af85";
    var name = "John Tolkien";
    var contributor = prepareContributor(name, typeId, nameTypeId, authorityId);
    var instance1 = new Instance().id(instanceId1).addContributorsItem(contributor);
    var instance2 = new Instance().id(instanceId2).addContributorsItem(contributor);
    inventoryApi.createInstance(TENANT_ID, instance1);
    inventoryApi.createInstance(TENANT_ID, instance2);
    assertSearchByIdsCount(instanceSearchPath(), List.of(instanceId1, instanceId2), 2, TENANT_ID);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CONTRIBUTOR, TENANT_ID))
      .as("Should have exactly 1 contributor document in the index")
      .hasSize(1));

    var sourceAsMap = fetchAllDocuments(INSTANCE_CONTRIBUTOR, TENANT_ID)[0].getSourceAsMap();
    asserContributorDocFields(sourceAsMap, name, nameTypeId, authorityId);
    assertContributorInstancesGroup(sourceAsMap, typeId);
  }

  @Test
  void shouldIndexInstanceContributor_deleteDocumentOnInstanceUpdate() {
    var instanceId = randomId();
    var contributor = new Contributor().name("Frodo Begins").authorityId(null);
    var instance = new Instance().id(instanceId).addContributorsItem(contributor);
    inventoryApi.createInstance(TENANT_ID, instance);
    assertSearchByIdsCount(instanceSearchPath(), List.of(instanceId), 1, TENANT_ID);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CONTRIBUTOR, TENANT_ID))
      .as("Should have exactly 1 contributor document before instance update")
      .hasSize(1));
    inventoryApi.updateInstance(TENANT_ID, instance.contributors(null));
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CONTRIBUTOR, TENANT_ID))
      .as("Contributor document should be removed after instance contributors cleared")
      .isEmpty());
  }

  @Test
  void shouldIndexInstanceContributor_deleteDocumentOnInstanceDelete() {
    var instanceId = randomId();
    var contributor = new Contributor().name("Frodo Begins").authorityId(null);
    var instance = new Instance().id(instanceId).addContributorsItem(contributor);
    inventoryApi.createInstance(TENANT_ID, instance);
    assertSearchByIdsCount(instanceSearchPath(), List.of(instanceId), 1, TENANT_ID);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CONTRIBUTOR, TENANT_ID))
      .as("Should have exactly 1 contributor document before instance delete")
      .hasSize(1));
    inventoryApi.deleteInstance(TENANT_ID, instanceId);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CONTRIBUTOR, TENANT_ID))
      .as("Contributor document should be removed after instance delete")
      .isEmpty());
  }

  @SuppressWarnings("unchecked")
  private void assertContributorInstancesGroup(Map<String, Object> sourceAsMap, String typeId) {
    var instances = (List<Map<String, Object>>) sourceAsMap.get("instances");
    assertThat(instances)
      .as("Instances list should contain exactly 1 group with count 2")
      .hasSize(1)
      .allSatisfy(map -> assertThat(map).containsEntry("shared", false))
      .allSatisfy(map -> assertThat(map).containsEntry("tenantId", TENANT_ID))
      .allSatisfy(map -> assertThat(map).containsEntry("typeId", List.of(typeId)))
      .allSatisfy(map -> assertThat(map).containsEntry("count", 2));
  }

  private Contributor prepareContributor(String name, String contributorTypeId, String nameTypeId, String authorityId) {
    return new Contributor()
      .name(name)
      .contributorTypeId(contributorTypeId)
      .contributorNameTypeId(nameTypeId)
      .authorityId(authorityId);
  }

  private void asserContributorDocFields(Map<String, Object> sourceAsMap, String name, String contributorNameTypeId,
                                         String authorityId) {
    assertThat(sourceAsMap)
      .as("Contributor document should contain expected indexed fields")
      .contains(
        entry("name", name),
        entry("contributorNameTypeId", contributorNameTypeId),
        entry("authorityId", authorityId)
      );
  }
}
