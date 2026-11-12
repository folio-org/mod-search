package org.folio.search.service.indexrebuild.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.service.indexrebuild.IndexRebuildTarget.CALL_NUMBER;
import static org.folio.search.service.indexrebuild.IndexRebuildTarget.ITEM;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.indexrebuild.IndexRebuildRangeIds;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.model.IndexRebuildRange;
import org.folio.search.service.indexrebuild.model.IndexRebuildRangeStatus;
import org.folio.search.service.indexrebuild.model.IndexRebuildState;
import org.folio.search.service.indexrebuild.model.IndexRebuildStatus;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.config.TestNoOpCacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@IntegrationTest
@JdbcTest
@EnablePostgres
@AutoConfigureJson
@Import(TestNoOpCacheConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IndexRebuildRepositoriesIT {

  private static final String ITEM_ID = "9f8febd1-e96c-46c4-a5f4-84a45cc499a2";

  private @Autowired JdbcTemplate jdbcTemplate;
  private @MockitoBean FolioExecutionContext context;
  private @MockitoBean ConsortiumTenantProvider tenantProvider;
  private IndexRebuildStateRepository stateRepository;
  private IndexRebuildRangeRepository rangeRepository;
  private DeletedRecordRepository deletedRecordRepository;
  private DirtyKeyRepository dirtyKeyRepository;

  @BeforeEach
  void setUp() {
    stateRepository = new IndexRebuildStateRepository(jdbcTemplate, context);
    rangeRepository = new IndexRebuildRangeRepository(jdbcTemplate, context);
    deletedRecordRepository = new DeletedRecordRepository(jdbcTemplate, context);
    dirtyKeyRepository = new DirtyKeyRepository(jdbcTemplate, context);
    when(context.getFolioModuleMetadata()).thenReturn(new FolioModuleMetadata() {
      @Override
      public String getModuleName() {
        return null;
      }

      @Override
      public String getDBSchemaName(String tenantId) {
        return "public";
      }
    });
    when(context.getTenantId()).thenReturn(TENANT_ID);
  }

  @Test
  void claim_positive_rejectsSecondClaimAndAllowsAfterFailure() {
    assertThat(stateRepository.claim(ITEM, TENANT_ID)).isTrue();
    assertThat(stateRepository.claim(ITEM, TENANT_ID)).isFalse();

    stateRepository.markFailed(ITEM, TENANT_ID, "error");

    assertThat(state().status()).isEqualTo(IndexRebuildStatus.FAILED);
    assertThat(state().failCause()).isEqualTo("error");
    assertThat(stateRepository.claim(ITEM, TENANT_ID)).isTrue();
    assertThat(state().failCause()).isNull();
  }

  @Test
  void claim_positive_otherTenantIsIndependent() {
    assertThat(stateRepository.claim(ITEM, TENANT_ID)).isTrue();
    assertThat(stateRepository.claim(ITEM, MEMBER_TENANT_ID)).isTrue();
  }

  @Test
  void tryStartFinalizing_positive_onlyWhenAllRangesSucceededAndOnlyOnce() {
    stateRepository.claim(ITEM, TENANT_ID);
    var range = saveRange(TENANT_ID);
    assertThat(stateRepository.tryStartFinalizing(ITEM, TENANT_ID)).isFalse();

    rangeRepository.markSuccess(range);

    assertThat(stateRepository.tryStartFinalizing(ITEM, TENANT_ID)).isTrue();
    assertThat(stateRepository.tryStartFinalizing(ITEM, TENANT_ID)).isFalse();
    assertThat(state().status()).isEqualTo(IndexRebuildStatus.FINALIZING);
  }

  @Test
  void tryStartFinalizing_negative_failedRangeBlocks() {
    stateRepository.claim(ITEM, TENANT_ID);
    rangeRepository.markFailed(saveRange(TENANT_ID), "error");

    assertThat(stateRepository.tryStartFinalizing(ITEM, TENANT_ID)).isFalse();
  }

  @Test
  void complete_positive_storesFingerprint() {
    stateRepository.claim(ITEM, TENANT_ID);
    stateRepository.tryStartFinalizing(ITEM, TENANT_ID);

    stateRepository.complete(ITEM, TENANT_ID, "fingerprint");

    assertThat(state().status()).isEqualTo(IndexRebuildStatus.COMPLETED);
    assertThat(state().fingerprint()).isEqualTo("fingerprint");
    assertThat(state().finishedAt()).isNotNull();
  }

  @Test
  void prepareRetry_positive_onlyFailedOrInterruptedRuns() {
    stateRepository.claim(ITEM, TENANT_ID);
    assertThat(stateRepository.prepareRetry(ITEM, TENANT_ID)).isFalse();

    stateRepository.markFailed(ITEM, TENANT_ID, "error");

    assertThat(stateRepository.prepareRetry(ITEM, TENANT_ID)).isTrue();
    assertThat(state().status()).isEqualTo(IndexRebuildStatus.IN_PROGRESS);
  }

  @Test
  void isTracking_positive_whileRunIsNotCompleted() {
    assertThat(stateRepository.isTracking(ITEM, TENANT_ID)).isFalse();

    stateRepository.claim(ITEM, TENANT_ID);
    assertThat(stateRepository.isTracking(ITEM, TENANT_ID)).isTrue();

    stateRepository.tryStartFinalizing(ITEM, TENANT_ID);
    stateRepository.complete(ITEM, TENANT_ID, "fingerprint");
    assertThat(stateRepository.isTracking(ITEM, TENANT_ID)).isFalse();
  }

  @Test
  void ranges_positive_lifecycle() {
    var succeeded = saveRange(TENANT_ID);
    var failed = saveRange(TENANT_ID);
    saveRange(MEMBER_TENANT_ID);

    rangeRepository.markSuccess(succeeded);
    rangeRepository.markFailed(failed, "error");

    assertThat(rangeRepository.findById(ITEM, failed).orElseThrow().status()).isEqualTo(IndexRebuildRangeStatus.FAILED);
    assertThat(rangeRepository.countByTenant(ITEM))
      .containsEntry(TENANT_ID, new IndexRebuildRangeRepository.RangeCounts(2, 1));

    rangeRepository.resetFailed(ITEM, TENANT_ID);

    assertThat(rangeRepository.findUnfinished(ITEM, TENANT_ID)).singleElement().satisfies(range -> {
      assertThat(range.id()).isEqualTo(failed);
      assertThat(range.status()).isEqualTo(IndexRebuildRangeStatus.PENDING);
    });
  }

  @Test
  void delete_positive_onlyRangesOfTenant() {
    saveRange(TENANT_ID);
    var memberRange = saveRange(MEMBER_TENANT_ID);

    rangeRepository.delete(ITEM, TENANT_ID);

    assertThat(rangeRepository.findUnfinished(ITEM, TENANT_ID)).isEmpty();
    assertThat(rangeRepository.findById(ITEM, memberRange)).isPresent();
  }

  @Test
  void deletedItems_positive_lifecycle() {
    deletedRecordRepository.add(ITEM, TENANT_ID, List.of(ITEM_ID));
    deletedRecordRepository.add(ITEM, TENANT_ID, List.of(ITEM_ID));
    deletedRecordRepository.add(ITEM, MEMBER_TENANT_ID, List.of(ITEM_ID));
    assertThat(deletedRecordRepository.findIds(ITEM, TENANT_ID)).containsExactly(ITEM_ID);

    deletedRecordRepository.remove(ITEM, TENANT_ID, List.of(ITEM_ID));
    assertThat(deletedRecordRepository.findIds(ITEM, TENANT_ID)).isEmpty();

    deletedRecordRepository.deleteAll(ITEM, MEMBER_TENANT_ID);
    assertThat(deletedRecordRepository.findIds(ITEM, MEMBER_TENANT_ID)).isEmpty();
  }

  @Test
  void tryStartAllFinalizing_positive_whenAllRangesOfTargetSucceeded() {
    stateRepository.claim(CALL_NUMBER, TENANT_ID);
    stateRepository.claim(CALL_NUMBER, MEMBER_TENANT_ID);
    var range = saveRange(CALL_NUMBER, TENANT_ID);
    assertThat(stateRepository.tryStartAllFinalizing(CALL_NUMBER)).isFalse();

    rangeRepository.markSuccess(range);

    assertThat(stateRepository.tryStartAllFinalizing(CALL_NUMBER)).isTrue();
    assertThat(stateRepository.tryStartAllFinalizing(CALL_NUMBER)).isFalse();
    stateRepository.completeAll(CALL_NUMBER, "fingerprint");
    assertThat(stateRepository.findAll(CALL_NUMBER)).hasSize(2)
      .allMatch(state -> state.status() == IndexRebuildStatus.COMPLETED);
  }

  @Test
  void markAllFailedAndRetry_positive_coverAllTenantsOfTarget() {
    stateRepository.claim(CALL_NUMBER, TENANT_ID);
    stateRepository.claim(CALL_NUMBER, MEMBER_TENANT_ID);
    assertThat(stateRepository.isAnyActive(CALL_NUMBER)).isTrue();

    stateRepository.markAllFailed(CALL_NUMBER, "error");
    assertThat(stateRepository.isAnyActive(CALL_NUMBER)).isFalse();

    assertThat(stateRepository.prepareAllRetry(CALL_NUMBER)).isTrue();
    assertThat(stateRepository.isAnyActive(CALL_NUMBER)).isTrue();
  }

  @Test
  void ranges_positive_targetWideOperations() {
    var failed = saveRange(CALL_NUMBER, TENANT_ID);
    final var item = saveRange(TENANT_ID);
    rangeRepository.markFailed(failed, "error");

    rangeRepository.resetAllFailed(CALL_NUMBER);
    assertThat(rangeRepository.findAllUnfinished(CALL_NUMBER)).singleElement()
      .satisfies(range -> assertThat(range.status()).isEqualTo(IndexRebuildRangeStatus.PENDING));

    rangeRepository.deleteAll(CALL_NUMBER);
    assertThat(rangeRepository.findAllUnfinished(CALL_NUMBER)).isEmpty();
    assertThat(rangeRepository.findById(ITEM, item)).isPresent();
  }

  @Test
  void dirtyKeys_positive_lifecycle() {
    dirtyKeyRepository.add(CALL_NUMBER, TENANT_ID, List.of("key1", "key2"));
    dirtyKeyRepository.add(CALL_NUMBER, MEMBER_TENANT_ID, List.of("key3"));
    var tenants = List.of(TENANT_ID);
    assertThat(dirtyKeyRepository.findBatch(CALL_NUMBER, tenants, 10)).containsExactlyInAnyOrder("key1", "key2");
    assertThat(dirtyKeyRepository.findBatch(CALL_NUMBER, tenants, 1)).hasSize(1);

    var later = Timestamp.from(Instant.now().plusSeconds(60));
    var earlier = Timestamp.from(Instant.now().minusSeconds(60));
    dirtyKeyRepository.delete(CALL_NUMBER, List.of("key1"), tenants, later);
    dirtyKeyRepository.delete(CALL_NUMBER, List.of("key2"), tenants, earlier);

    assertThat(dirtyKeyRepository.findBatch(CALL_NUMBER, List.of(TENANT_ID, MEMBER_TENANT_ID), 10))
      .containsExactlyInAnyOrder("key2", "key3");
  }

  @Test
  void dirtyKeys_positive_queuesOfTargetsAreSeparate() {
    dirtyKeyRepository.add(CALL_NUMBER, TENANT_ID, List.of("key"));
    dirtyKeyRepository.add(ITEM, TENANT_ID, List.of("key", "other"));

    dirtyKeyRepository.delete(ITEM, List.of("key", "other"), List.of(TENANT_ID),
      Timestamp.from(Instant.now().plusSeconds(60)));

    assertThat(dirtyKeyRepository.findBatch(CALL_NUMBER, List.of(TENANT_ID), 10)).containsExactly("key");
    assertThat(dirtyKeyRepository.findBatch(ITEM, List.of(TENANT_ID), 10)).isEmpty();
  }

  @Test
  void dirtyKeys_positive_enqueueTimeComesFromDatabaseClock() {
    var before = dirtyKeyRepository.currentTime();
    dirtyKeyRepository.add(CALL_NUMBER, TENANT_ID, List.of("key"));
    var after = dirtyKeyRepository.currentTime();

    dirtyKeyRepository.delete(CALL_NUMBER, List.of("key"), List.of(TENANT_ID), before);
    assertThat(dirtyKeyRepository.findBatch(CALL_NUMBER, List.of(TENANT_ID), 10)).containsExactly("key");

    dirtyKeyRepository.delete(CALL_NUMBER, List.of("key"), List.of(TENANT_ID), after);
    assertThat(dirtyKeyRepository.findBatch(CALL_NUMBER, List.of(TENANT_ID), 10)).isEmpty();
  }

  @Test
  void dirtyKeys_positive_lockIsReentrantWithinTransaction() {
    assertThat(dirtyKeyRepository.tryLock(CALL_NUMBER)).isTrue();
    assertThat(dirtyKeyRepository.tryLock(CALL_NUMBER)).isTrue();
  }

  private IndexRebuildState state() {
    return stateRepository.findAll(ITEM).getFirst();
  }

  private UUID saveRange(String tenantId) {
    return saveRange(ITEM, tenantId);
  }

  private UUID saveRange(IndexRebuildTarget target, String tenantId) {
    var id = IndexRebuildRangeIds.newId();
    rangeRepository.saveAll(List.of(new IndexRebuildRange(id, UUID.randomUUID(), target, tenantId, "00", "ff",
      IndexRebuildRangeStatus.PENDING, null)));
    return id;
  }
}
