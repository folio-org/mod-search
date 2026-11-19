package org.folio.indexing;

import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CALL_NUMBERS;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER2_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;

import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.TestPropertySource;

/**
 * Suite for consortium indexing tests. These tests create and change data on their own, so they are kept apart
 * from the read-only {@code SearchBrowseConsortiumSuiteIT}; each test scopes its assertions to its own records.
 * The properties are the same as in {@code IndexingSuiteIT}, so the Spring context is shared with it.
 *
 * <p>Item indexing tests ({@code IndexingItemConsortiumIT}, like the non-consortium {@code IndexingItemIT}) do not
 * run here: they need {@code item-index-enabled}, which changes how the module indexes item events, so they run in
 * their own Spring context together with the item reindex tests. Read-only item search checks run in
 * {@code SearchBrowseConsortiumSuiteIT} ({@code SearchInventoryItemConsortiumIT}).
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
    enableTenant(MEMBER2_TENANT_ID);
    enableFeature(CENTRAL_TENANT_ID, BROWSE_CALL_NUMBERS);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant(MEMBER2_TENANT_ID);
    removeTenant(MEMBER_TENANT_ID);
    removeTenant(CENTRAL_TENANT_ID);
  }

  @Nested
  class IndexingInstanceCallNumber extends IndexingInstanceCallNumberConsortiumIT { }

  @Nested
  class IndexingLocations extends IndexingLocationsConsortiumIT { }
}
