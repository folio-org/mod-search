package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.folio.search.model.reindex.runtime.V2ReindexPhaseType;
import org.folio.search.model.reindex.runtime.V2ReindexResourceType;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.model.types.QueryVersion;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class V2ReindexRuntimeStatusTrackerTest {

  private final V2ReindexRuntimeStatusTracker tracker = new V2ReindexRuntimeStatusTracker();

  @Test
  void mutatorsDoNotCreateSnapshotsWhenFamilyWasNotTrackedInThisJvm() {
    var familyId = UUID.randomUUID();

    tracker.resumeFamily(familyId, IndexFamilyStatus.STAGED);
    tracker.updateFamilyStatus(familyId, IndexFamilyStatus.ACTIVE);
    tracker.startPhase(familyId, V2ReindexPhaseType.BROWSE_REBUILD, 5L, Map.of("alias", "folio"));
    tracker.advancePhase(familyId, V2ReindexPhaseType.CATCH_UP, 1L, 0L);
    tracker.updatePhaseDetails(familyId, V2ReindexPhaseType.CUTOVER, Map.of("newIndex", "folio_instance"));
    tracker.startResource(familyId, V2ReindexResourceType.INSTANCE);
    tracker.recordResourceBatch(familyId, V2ReindexResourceType.INSTANCE, 10, 20L, 5L, 4L, 3L);
    tracker.completeResource(familyId, V2ReindexResourceType.INSTANCE);
    tracker.markFamilyCompleted(familyId);

    assertThat(tracker.find(familyId)).isEmpty();
    assertThat(tracker.findAll()).isEmpty();
  }

  @Test
  void resumeFamilyClearsTerminalFailureForTrackedSnapshot() {
    var familyId = UUID.randomUUID();
    var jobId = UUID.randomUUID();

    tracker.startFamily(familyId, jobId, "consortium", QueryVersion.V2);
    tracker.markFamilyFailed(familyId, "boom");
    tracker.resumeFamily(familyId, IndexFamilyStatus.STAGED);
    tracker.startPhase(familyId, V2ReindexPhaseType.CATCH_UP, 1L);

    var snapshot = tracker.find(familyId).orElseThrow();
    assertThat(snapshot.getStatus().name()).isEqualTo("IN_PROGRESS");
    assertThat(snapshot.getErrorMessage()).isNull();
    assertThat(snapshot.getTerminalAt()).isNull();
    assertThat(snapshot.getFamilyStatus()).isEqualTo(IndexFamilyStatus.STAGED);
    assertThat(snapshot.phase(V2ReindexPhaseType.CATCH_UP).getStatus().name()).isEqualTo("IN_PROGRESS");
  }
}
