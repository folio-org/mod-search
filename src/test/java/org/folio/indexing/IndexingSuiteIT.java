package org.folio.indexing;

import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CALL_NUMBERS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CLASSIFICATIONS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CONTRIBUTORS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_SUBJECTS;
import static org.folio.search.model.types.ResourceType.AUTHORITY;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.model.types.ResourceType.INSTANCE_CALL_NUMBER;
import static org.folio.search.model.types.ResourceType.INSTANCE_CLASSIFICATION;
import static org.folio.search.model.types.ResourceType.INSTANCE_CONTRIBUTOR;
import static org.folio.search.model.types.ResourceType.INSTANCE_SUBJECT;
import static org.folio.support.TestConstants.TENANT_ID;

import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = {
  "folio.search-config.indexing.instance-children-index-enabled=true"
})
class IndexingSuiteIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    enableTenant(TENANT_ID);
    enableFeature(TENANT_ID, BROWSE_SUBJECTS);
    enableFeature(TENANT_ID, BROWSE_CALL_NUMBERS);
    enableFeature(TENANT_ID, BROWSE_CONTRIBUTORS);
    enableFeature(TENANT_ID, BROWSE_CLASSIFICATIONS);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    deleteAllDocuments(AUTHORITY, TENANT_ID);
    deleteAllDocuments(INSTANCE, TENANT_ID);
    deleteAllDocuments(INSTANCE_SUBJECT, TENANT_ID);
    deleteAllDocuments(INSTANCE_CALL_NUMBER, TENANT_ID);
    deleteAllDocuments(INSTANCE_CONTRIBUTOR, TENANT_ID);
    deleteAllDocuments(INSTANCE_CLASSIFICATION, TENANT_ID);
  }

  @Nested
  class IndexingAuthority extends IndexingAuthorityIT { }

  @Nested
  class IndexingInstance extends IndexingInstanceIT { }

  @Nested
  class IndexingInstanceCallNumber extends IndexingInstanceCallNumberIT { }

  @Nested
  class IndexingInstanceClassification extends IndexingInstanceClassificationIT { }

  @Nested
  class IndexingInstanceContributor extends IndexingInstanceContributorIT { }

  @Nested
  class IndexingInstanceSubject extends IndexingInstanceSubjectIT { }
}
