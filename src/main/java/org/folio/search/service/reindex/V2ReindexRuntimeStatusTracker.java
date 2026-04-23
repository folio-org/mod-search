package org.folio.search.service.reindex;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.reindex.runtime.V2ReindexFamilyRuntimeSnapshot;
import org.folio.search.model.reindex.runtime.V2ReindexPhaseType;
import org.folio.search.model.reindex.runtime.V2ReindexResourceType;
import org.folio.search.model.reindex.runtime.V2ReindexRuntimeStatus;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.model.types.QueryVersion;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class V2ReindexRuntimeStatusTracker {

  private static final Duration TERMINAL_RETENTION = Duration.ofHours(12);
  private static final int MAX_TERMINAL_SNAPSHOTS = 200;

  private final ConcurrentMap<UUID, V2ReindexFamilyRuntimeSnapshot> snapshots = new ConcurrentHashMap<>();

  public V2ReindexFamilyRuntimeSnapshot startFamily(UUID familyId, UUID jobId, String tenantId,
                                                    QueryVersion queryVersion) {
    var now = Instant.now();
    var snapshot = snapshots.computeIfAbsent(familyId, ignored -> new V2ReindexFamilyRuntimeSnapshot());
    synchronized (snapshot) {
      snapshot.reset(familyId, jobId, tenantId, queryVersion, now);
    }
    cleanupTerminalSnapshots();
    return snapshot;
  }

  public Optional<V2ReindexFamilyRuntimeSnapshot> find(UUID familyId) {
    cleanupTerminalSnapshots();
    var snapshot = snapshots.get(familyId);
    if (snapshot == null) {
      return Optional.empty();
    }
    synchronized (snapshot) {
      return Optional.of(snapshot.copy());
    }
  }

  public List<V2ReindexFamilyRuntimeSnapshot> findAll() {
    cleanupTerminalSnapshots();
    return snapshots.values().stream()
      .map(this::copySnapshot)
      .toList();
  }

  public void resumeFamily(UUID familyId, IndexFamilyStatus familyStatus) {
    mutateIfTracked(familyId, (snapshot, now) -> snapshot.resume(familyStatus, now));
  }

  public void updateFamilyStatus(UUID familyId, IndexFamilyStatus familyStatus) {
    mutateIfTracked(familyId, (snapshot, now) -> snapshot.updateFamilyStatus(familyStatus, now));
  }

  public void startPhase(UUID familyId, V2ReindexPhaseType phaseType, long totalSteps) {
    startPhase(familyId, phaseType, totalSteps, null);
  }

  public void startPhase(UUID familyId, V2ReindexPhaseType phaseType, long totalSteps, Map<String, Object> details) {
    mutateIfTracked(familyId, (snapshot, now) -> {
      var phase = snapshot.phase(phaseType);
      phase.totalSteps(totalSteps);
      if (details != null && !details.isEmpty()) {
        phase.setDetails(details);
      }
      phase.start(now);
      snapshot.touch(now);
    });
  }

  public void advancePhase(UUID familyId, V2ReindexPhaseType phaseType, long completedSteps,
                           Long observedValue) {
    mutateIfTracked(familyId, (snapshot, now) -> {
      snapshot.phase(phaseType).advance(completedSteps, observedValue, now);
      snapshot.touch(now);
    });
  }

  public void completePhase(UUID familyId, V2ReindexPhaseType phaseType) {
    mutateIfTracked(familyId, (snapshot, now) -> {
      snapshot.phase(phaseType).complete(now);
      snapshot.touch(now);
    });
  }

  public void failPhase(UUID familyId, V2ReindexPhaseType phaseType, String errorMessage) {
    mutateIfTracked(familyId, (snapshot, now) -> {
      snapshot.phase(phaseType).fail(errorMessage, now);
      snapshot.markTerminal(V2ReindexRuntimeStatus.FAILED, now, errorMessage);
    });
  }

  public void startResource(UUID familyId, V2ReindexResourceType resourceType) {
    mutateIfTracked(familyId, (snapshot, now) -> {
      snapshot.resource(resourceType).start(now);
      snapshot.touch(now);
    });
  }

  public void recordResourceBatch(UUID familyId, V2ReindexResourceType resourceType, int batchSize, long batchMs,
                                  long enrichMs, long convertMs, long osBulkMs) {
    mutateIfTracked(familyId, (snapshot, now) -> {
      snapshot.resource(resourceType).recordBatch(batchSize, batchMs, enrichMs, convertMs, osBulkMs, now);
      snapshot.touch(now);
    });
  }

  public void completeResource(UUID familyId, V2ReindexResourceType resourceType) {
    mutateIfTracked(familyId, (snapshot, now) -> {
      snapshot.resource(resourceType).complete(now);
      snapshot.touch(now);
    });
  }

  public void failResource(UUID familyId, V2ReindexResourceType resourceType, String errorMessage) {
    mutateIfTracked(familyId, (snapshot, now) -> {
      snapshot.resource(resourceType).fail(errorMessage, now);
      snapshot.markTerminal(V2ReindexRuntimeStatus.FAILED, now, errorMessage);
    });
  }

  public void markFamilyCompleted(UUID familyId) {
    mutateIfTracked(familyId, (snapshot, now) -> snapshot.markTerminal(V2ReindexRuntimeStatus.COMPLETED, now, null));
  }

  public void markFamilyFailed(UUID familyId, String errorMessage) {
    mutateIfTracked(familyId, (snapshot, now) -> snapshot.markTerminal(V2ReindexRuntimeStatus.FAILED, now,
      errorMessage));
  }

  public void recordCatchUpLag(UUID familyId, long lag) {
    advancePhase(familyId, V2ReindexPhaseType.CATCH_UP, 0L, lag);
  }

  public void startCutover(UUID familyId) {
    startPhase(familyId, V2ReindexPhaseType.CUTOVER, 1L);
  }

  public void updatePhaseDetails(UUID familyId, V2ReindexPhaseType phaseType, Map<String, Object> details) {
    mutateIfTracked(familyId, (snapshot, now) -> {
      var phase = snapshot.phase(phaseType);
      details.forEach(phase::putDetail);
      snapshot.touch(now);
    });
  }

  public void removeFamily(UUID familyId) {
    snapshots.remove(familyId);
  }

  private void mutateIfTracked(UUID familyId, SnapshotMutation action) {
    var snapshot = snapshots.get(familyId);
    if (snapshot == null) {
      return;
    }

    synchronized (snapshot) {
      action.apply(snapshot, Instant.now());
    }
    cleanupTerminalSnapshots();
  }

  private void cleanupTerminalSnapshots() {
    var terminalSnapshots = snapshots.values().stream()
      .filter(V2ReindexFamilyRuntimeSnapshot::isTerminal)
      .sorted(Comparator.comparing(V2ReindexFamilyRuntimeSnapshot::getTerminalAt,
        Comparator.nullsLast(Comparator.naturalOrder())))
      .toList();

    var cutoff = Instant.now().minus(TERMINAL_RETENTION);
    for (var snapshot : terminalSnapshots) {
      synchronized (snapshot) {
        var terminalAt = snapshot.getTerminalAt();
        if (terminalAt != null && terminalAt.isBefore(cutoff)) {
          snapshots.remove(snapshot.getFamilyId(), snapshot);
        }
      }
    }

    var retainedTerminalSnapshots = snapshots.values().stream()
      .filter(V2ReindexFamilyRuntimeSnapshot::isTerminal)
      .sorted(Comparator.comparing(V2ReindexFamilyRuntimeSnapshot::getTerminalAt,
        Comparator.nullsLast(Comparator.naturalOrder())))
      .toList();

    if (retainedTerminalSnapshots.size() <= MAX_TERMINAL_SNAPSHOTS) {
      return;
    }

    var toRemove = retainedTerminalSnapshots.subList(0, retainedTerminalSnapshots.size() - MAX_TERMINAL_SNAPSHOTS);
    for (var snapshot : new ArrayList<>(toRemove)) {
      snapshots.remove(snapshot.getFamilyId(), snapshot);
    }
  }

  private V2ReindexFamilyRuntimeSnapshot copySnapshot(V2ReindexFamilyRuntimeSnapshot snapshot) {
    synchronized (snapshot) {
      return snapshot.copy();
    }
  }

  @FunctionalInterface
  private interface SnapshotMutation {
    void apply(V2ReindexFamilyRuntimeSnapshot snapshot, Instant now);
  }
}
