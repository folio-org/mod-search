package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.folio.search.model.reindex.IndexFamilyEntity;
import org.folio.search.model.reindex.runtime.V2ReindexPhaseType;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.service.IndexFamilyService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class V2IndexFamilyRuntimeStatusServiceTest {

  @Mock
  private IndexFamilyService indexFamilyService;
  @Mock
  private ReindexKafkaConsumerManager consumerManager;

  private V2ReindexRuntimeStatusTracker runtimeStatusTracker;
  private V2IndexFamilyRuntimeStatusService service;

  @BeforeEach
  void setUp() {
    runtimeStatusTracker = new V2ReindexRuntimeStatusTracker();
    service = new V2IndexFamilyRuntimeStatusService(indexFamilyService, consumerManager, runtimeStatusTracker);
  }

  @Test
  void getStatus_returnsUnavailableWhenFamilyWasNotTrackedInThisJvm() {
    var familyId = UUID.randomUUID();

    when(indexFamilyService.findById(familyId)).thenReturn(Optional.of(family(familyId, IndexFamilyStatus.STAGED)));

    runtimeStatusTracker.startPhase(familyId, V2ReindexPhaseType.CUTOVER, 1L);

    var response = service.getStatus(familyId);

    assertThat(response.currentPhase()).isEqualTo("UNAVAILABLE");
    assertThat(response.summary().trackedInMemory()).isFalse();
    assertThat(response.summary().message()).contains("unavailable");
    assertThat(response.details()).isNull();
  }

  @Test
  void getStatus_reportsResumedCatchUpInsteadOfStaleFailure() {
    var familyId = UUID.randomUUID();
    var jobId = UUID.randomUUID();

    when(indexFamilyService.findById(familyId)).thenReturn(Optional.of(family(familyId, IndexFamilyStatus.STAGED)));
    when(consumerManager.isConsumerRunning(familyId)).thenReturn(true);

    runtimeStatusTracker.startFamily(familyId, jobId, "consortium", QueryVersion.V2);
    runtimeStatusTracker.markFamilyFailed(familyId, "stale failure");
    runtimeStatusTracker.resumeFamily(familyId, IndexFamilyStatus.STAGED);
    runtimeStatusTracker.startPhase(familyId, V2ReindexPhaseType.CATCH_UP, 1L);

    var response = service.getStatus(familyId);

    assertThat(response.currentPhase()).isEqualTo("CATCHING_UP");
    assertThat(response.summary().failure()).isNull();
    assertThat(response.summary().liveMetricsAvailable()).isTrue();
    assertThat(response.details().phases().catchUp().status()).isEqualTo("IN_PROGRESS");
  }

  @Test
  void getStatus_reportsBrowseRebuildForActiveTrackedFamily() {
    var familyId = UUID.randomUUID();
    var jobId = UUID.randomUUID();

    when(indexFamilyService.findById(familyId)).thenReturn(Optional.of(family(familyId, IndexFamilyStatus.ACTIVE)));
    when(consumerManager.isConsumerRunning(familyId)).thenReturn(false);

    runtimeStatusTracker.startFamily(familyId, jobId, "diku", QueryVersion.V2);
    runtimeStatusTracker.updateFamilyStatus(familyId, IndexFamilyStatus.ACTIVE);
    runtimeStatusTracker.markFamilyCompleted(familyId);
    runtimeStatusTracker.startPhase(familyId, V2ReindexPhaseType.BROWSE_REBUILD, 5L);

    var response = service.getStatus(familyId);

    assertThat(response.currentPhase()).isEqualTo("BROWSE_REBUILD");
    assertThat(response.summary().liveMetricsAvailable()).isTrue();
    assertThat(response.details().phases().browseRebuild().status()).isEqualTo("IN_PROGRESS");
  }

  @Test
  void getStatus_keepsActivePhaseWhenStandaloneBrowseRebuildFails() {
    var familyId = UUID.randomUUID();
    var jobId = UUID.randomUUID();

    when(indexFamilyService.findById(familyId)).thenReturn(Optional.of(family(familyId, IndexFamilyStatus.ACTIVE)));
    when(consumerManager.isConsumerRunning(familyId)).thenReturn(false);

    runtimeStatusTracker.startFamily(familyId, jobId, "diku", QueryVersion.V2);
    runtimeStatusTracker.updateFamilyStatus(familyId, IndexFamilyStatus.ACTIVE);
    runtimeStatusTracker.markFamilyCompleted(familyId);
    runtimeStatusTracker.startPhase(familyId, V2ReindexPhaseType.BROWSE_REBUILD, 5L);
    runtimeStatusTracker.failPhase(familyId, V2ReindexPhaseType.BROWSE_REBUILD, "browse boom");

    var response = service.getStatus(familyId);

    assertThat(response.currentPhase()).isEqualTo("ACTIVE");
    assertThat(response.summary().failure()).isNotNull();
    assertThat(response.summary().failure().phase()).isEqualTo("BROWSE_REBUILD");
  }

  @Test
  void getStatus_reportsCatchUpSplitWhenLagHasReachedZeroBeforeManualSwitch() {
    var familyId = UUID.randomUUID();
    var snapshotCapturedAt = Instant.parse("2026-04-22T20:08:15Z");

    when(indexFamilyService.findById(familyId)).thenReturn(Optional.of(family(familyId, IndexFamilyStatus.STAGED)));
    when(consumerManager.hasStagedCutoverSnapshot(familyId)).thenReturn(true);
    when(consumerManager.getStagedCutoverSnapshotCapturedAt(familyId)).thenReturn(Optional.of(snapshotCapturedAt));
    when(consumerManager.getStagedCutoverSnapshotPartitionCount(familyId)).thenReturn(200);
    when(consumerManager.isConsumerRunning(familyId)).thenReturn(true);
    when(consumerManager.getConsumerLag(familyId)).thenReturn(0L);
    when(consumerManager.getConsumerLagToStagedCutoverSnapshot(familyId)).thenReturn(0L);

    var jobId = UUID.randomUUID();
    runtimeStatusTracker.startFamily(familyId, jobId, "diku", QueryVersion.V2);
    runtimeStatusTracker.startPhase(familyId, V2ReindexPhaseType.CATCH_UP, 200L);

    var response = service.getStatus(familyId);
    var details = response.details().phases().catchUp().details();

    assertThat(response.currentPhase()).isEqualTo("CATCHING_UP");
    assertThat(details.get("consumerLag")).isEqualTo(0L);
    assertThat(details.get("consumerLagToTarget")).isEqualTo(0L);
    assertThat(details.get("snapshotCapturedAt")).isEqualTo(snapshotCapturedAt.toString());
    assertThat(details.get("targetPartitions")).isEqualTo(200);
    assertThat(details.get("partitions")).isEqualTo(200);
    assertThat(details.get("readyForSwitchOver")).isEqualTo(true);
    assertThat(details.get("lagMeasuredAgainst")).isEqualTo("stagedSnapshot");
    assertThat(details.get("lagReachedZeroAt")).isInstanceOf(String.class);
    assertThat(details.get("timeUntilLagZeroMs")).isInstanceOf(Long.class);
    assertThat(details.get("timeWaitingForManualSwitchMs")).isInstanceOf(Long.class);
    assertThat((Long) details.get("timeUntilLagZeroMs")).isGreaterThanOrEqualTo(0L);
    assertThat((Long) details.get("timeWaitingForManualSwitchMs")).isGreaterThanOrEqualTo(0L);
  }

  @Test
  void getStatus_reportsCompletedCatchUpSplitAfterManualSwitch() {
    var familyId = UUID.randomUUID();
    var jobId = UUID.randomUUID();

    when(indexFamilyService.findById(familyId)).thenReturn(Optional.of(family(familyId, IndexFamilyStatus.ACTIVE)));
    when(consumerManager.isConsumerRunning(familyId)).thenReturn(false);

    runtimeStatusTracker.startFamily(familyId, jobId, "diku", QueryVersion.V2);
    runtimeStatusTracker.startPhase(familyId, V2ReindexPhaseType.CATCH_UP, 200L);
    runtimeStatusTracker.markCatchUpReady(familyId);
    runtimeStatusTracker.completePhase(familyId, V2ReindexPhaseType.CATCH_UP);
    runtimeStatusTracker.markFamilyCompleted(familyId);

    var response = service.getStatus(familyId);
    var details = response.details().phases().catchUp().details();

    assertThat(details.get("consumerLag")).isEqualTo(0L);
    assertThat(details.get("consumerLagToTarget")).isEqualTo(0L);
    assertThat(details.get("lagReachedZeroAt")).isInstanceOf(String.class);
    assertThat(details.get("timeUntilLagZeroMs")).isInstanceOf(Long.class);
    assertThat(details.get("timeWaitingForManualSwitchMs")).isInstanceOf(Long.class);
    assertThat((Long) details.get("timeUntilLagZeroMs")).isGreaterThanOrEqualTo(0L);
    assertThat((Long) details.get("timeWaitingForManualSwitchMs")).isGreaterThanOrEqualTo(0L);
  }

  private static IndexFamilyEntity family(UUID familyId, IndexFamilyStatus status) {
    return new IndexFamilyEntity(
      familyId,
      3,
      "folio_instance_search_diku_3",
      status,
      Timestamp.from(Instant.now()),
      status == IndexFamilyStatus.ACTIVE ? Timestamp.from(Instant.now()) : null,
      null,
      QueryVersion.V2
    );
  }
}
