package org.folio.indexing;

import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CALL_NUMBERS;
import static org.folio.search.service.reindex.ReindexConstants.CALL_NUMBER_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.HOLDING_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.INSTANCE_CALL_NUMBER_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.INSTANCE_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.ITEM_TABLE;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;

import org.folio.spring.testing.extension.DatabaseCleanup;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.TestPropertySource;

/**
 * Suite for consortium indexing tests. These tests create and wipe data on their own, so they are kept apart
 * from the read-only {@code SearchBrowseConsortiumSuiteIT}; the Spring context is shared with
 * {@link IndexingSuiteIT} because both use the same properties.
 */
@IntegrationTest
@TestPropertySource(properties = {
  "folio.search-config.indexing.instance-children-index-enabled=true"
})
class ConsortiumIndexingSuiteIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    enableTenant(CENTRAL_TENANT_ID);
    enableTenant(MEMBER_TENANT_ID);
    enableFeature(CENTRAL_TENANT_ID, BROWSE_CALL_NUMBERS);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant(MEMBER_TENANT_ID);
    removeTenant(CENTRAL_TENANT_ID);
  }

  @Nested
  @DatabaseCleanup(tenants = CENTRAL_TENANT_ID,
    tables = {CALL_NUMBER_TABLE, INSTANCE_CALL_NUMBER_TABLE, ITEM_TABLE, HOLDING_TABLE, INSTANCE_TABLE})
  class IndexingInstanceCallNumber extends IndexingInstanceCallNumberConsortiumIT { }
}
