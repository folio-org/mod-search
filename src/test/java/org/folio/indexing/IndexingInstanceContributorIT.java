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
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.instance-children-index-enabled=true")
class IndexingInstanceContributorIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant();
    enableFeature(TenantConfiguredFeature.BROWSE_CONTRIBUTORS);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

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
    assertCountByIds(instanceSearchPath(), List.of(instanceId1, instanceId2), 2);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CONTRIBUTOR, TENANT_ID)).hasSize(1));

    var sourceAsMap = fetchAllDocuments(INSTANCE_CONTRIBUTOR, TENANT_ID)[0].getSourceAsMap();
    asserContributorDocFields(sourceAsMap, name, nameTypeId, authorityId);

    @SuppressWarnings("unchecked")
    var instances = (List<Map<String, Object>>) sourceAsMap.get("instances");
    assertThat(instances)
      .hasSize(1)
      .allSatisfy(map -> assertThat(map).containsEntry("shared", false))
      .allSatisfy(map -> assertThat(map).containsEntry("tenantId", TENANT_ID))
      .allSatisfy(map -> assertThat(map).containsEntry("typeId", List.of(typeId)))
      .allSatisfy(map -> assertThat(map).containsEntry("count", 2));
  }

  @Test
  void shouldIndexInstanceContributor_deleteDocumentOnInstanceUpdate() {
    var instanceId = randomId();
    var contributor = new Contributor().name("Frodo Begins").authorityId(null);
    var instance = new Instance().id(instanceId).addContributorsItem(contributor);
    inventoryApi.createInstance(TENANT_ID, instance);
    assertCountByIds(instanceSearchPath(), List.of(instanceId), 1);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CONTRIBUTOR, TENANT_ID)).hasSize(1));
    inventoryApi.updateInstance(TENANT_ID, instance.contributors(null));
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CONTRIBUTOR, TENANT_ID)).isEmpty());
  }

  @Test
  void shouldIndexInstanceContributor_deleteDocumentOnInstanceDelete() {
    var instanceId = randomId();
    var contributor = new Contributor().name("Frodo Begins").authorityId(null);
    var instance = new Instance().id(instanceId).addContributorsItem(contributor);
    inventoryApi.createInstance(TENANT_ID, instance);
    assertCountByIds(instanceSearchPath(), List.of(instanceId), 1);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CONTRIBUTOR, TENANT_ID)).hasSize(1));
    inventoryApi.deleteInstance(TENANT_ID, instanceId);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CONTRIBUTOR, TENANT_ID)).isEmpty());
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
      .contains(
        entry("name", name),
        entry("contributorNameTypeId", contributorNameTypeId),
        entry("authorityId", authorityId)
      );
  }
}
