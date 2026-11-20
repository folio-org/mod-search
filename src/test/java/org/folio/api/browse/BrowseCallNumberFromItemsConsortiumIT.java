package org.folio.api.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER2_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.Arrays;
import java.util.List;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.IndexRebuildRequest;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.indexrebuild.IndexFingerprintService;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.search.service.scheduled.ScheduledInstanceSubResourcesService;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.folio.support.base.EcsInventorySetup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opensearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Runs the {@link BrowseCallNumberConsortiumIT} checks with call number documents built from the item index: at
 * runtime by the call number refresh, and by the call number records reindex.
 *
 * <p>The properties change how the module indexes call numbers, so this class does not run in
 * {@code SearchBrowseConsortiumSuiteIT} and has its own Spring context and tenants. It loads the same shared ECS data
 * set (loaded by {@link EcsInventorySetup#setUp}), so the expectations are the same.
 */
@IntegrationTest
@TestPropertySource(properties = {
  "folio.search-config.indexing.instance-children-index-enabled=true",
  "folio.search-config.indexing.item-index-enabled=true",
  "folio.search-config.indexing.call-number-index-from-items-enabled=true",
  "folio.search-config.indexing.call-number-refresh-delay-ms=500"
})
class BrowseCallNumberFromItemsConsortiumIT extends BaseIntegrationTest {

  private static final String CALL_NUMBER_REINDEX_PATH = "/search/index/call-number-records/reindex/full";
  private static final String CALL_NUMBER_REINDEX_STATUS_PATH = "/search/index/call-number-records/reindex/status";
  private static final int MAX_FETCHED_DOCUMENTS = 1000;

  @BeforeAll
  static void prepare(@Autowired JdbcTemplate jdbcTemplate, @Autowired IndexFingerprintService fingerprintService,
                      @Autowired SubResourcesLockRepository lockRepository,
                      @Autowired ScheduledInstanceSubResourcesService scheduledSubResourcesService,
                      @Autowired SearchConfigurationProperties properties) {
    enableTenant(CENTRAL_TENANT_ID);
    enableTenant(MEMBER_TENANT_ID);
    enableTenant(MEMBER2_TENANT_ID);
    // item documents come from the item events of the data set, mark the item index of every tenant as up to date,
    // so the call number refresh doesn't wait for an item records reindex
    markItemIndexReady(jdbcTemplate, fingerprintService.current(IndexRebuildTarget.ITEM));
    EcsInventorySetup.setUp(lockRepository, scheduledSubResourcesService,
      (int) properties.getMaxSearchBatchRequestIdsCount());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant(MEMBER2_TENANT_ID);
    removeTenant(MEMBER_TENANT_ID);
    removeTenant(CENTRAL_TENANT_ID);
  }

  @Test
  void callNumberRecordsReindex_producesSameDocumentsAsRuntimeRefresh() {
    var refreshed = callNumberDocuments();

    doPost(CALL_NUMBER_REINDEX_PATH, CENTRAL_TENANT_ID, new IndexRebuildRequest());
    awaitAssertion(() -> doGet(CALL_NUMBER_REINDEX_STATUS_PATH, CENTRAL_TENANT_ID)
      .andExpect(jsonPath("$[*].status", everyItem(is("COMPLETED")))));

    assertThat(callNumberDocuments()).containsExactlyInAnyOrderElementsOf(refreshed);
  }

  private static List<String> callNumberDocuments() {
    return Arrays.stream(fetchDocuments(ResourceType.INSTANCE_CALL_NUMBER, CENTRAL_TENANT_ID, MAX_FETCHED_DOCUMENTS))
      .map(SearchHit::getSourceAsMap)
      .map(String::valueOf)
      .sorted()
      .toList();
  }

  private static void markItemIndexReady(JdbcTemplate jdbcTemplate, String fingerprint) {
    for (var tenant : List.of(CENTRAL_TENANT_ID, MEMBER_TENANT_ID, MEMBER2_TENANT_ID)) {
      jdbcTemplate.update("INSERT INTO " + CENTRAL_TENANT_ID + "_mod_search.index_rebuild_state "
                          + "(resource, tenant_id, status, fingerprint) VALUES (?, ?, 'COMPLETED', ?)",
        IndexRebuildTarget.ITEM.getResource(), tenant, fingerprint);
    }
  }

  @Nested
  class BrowseCallNumber extends BrowseCallNumberConsortiumIT { }
}
