package org.folio.search.service.reindex;

import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.reindex.IndexFamilyEntity;
import org.folio.search.model.reindex.runtime.V2ReindexFamilyRuntimeSnapshot;
import org.folio.search.model.reindex.runtime.V2ReindexPhaseRuntime;
import org.folio.search.model.reindex.runtime.V2ReindexPhaseType;
import org.folio.search.model.reindex.runtime.V2ReindexResourceRuntime;
import org.folio.search.model.reindex.runtime.V2ReindexResourceType;
import org.folio.search.model.reindex.runtime.V2ReindexRuntimeStatus;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.service.IndexFamilyService;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class V2IndexFamilyRuntimeStatusService {

  private static final String UNAVAILABLE_MESSAGE = "Detailed runtime status is unavailable in this process.";

  private final IndexFamilyService indexFamilyService;
  private final ReindexKafkaConsumerManager consumerManager;
  @Nullable
  private final V2ReindexRuntimeStatusTracker runtimeStatusTracker;

  public IndexFamilyRuntimeStatusResponse getStatus(UUID familyId) {
    var family = indexFamilyService.findById(familyId)
      .orElseThrow(() -> new EntityNotFoundException("Index family not found: " + familyId));

    if (family.getQueryVersion() == QueryVersion.V1) {
      throw new RequestValidationException(
        "Detailed status breakdown is only supported for V2+ families",
        "queryVersion", family.getQueryVersion().getValue());
    }

    var snapshot = runtimeStatusTracker == null ? Optional.<V2ReindexFamilyRuntimeSnapshot>empty()
      : runtimeStatusTracker.find(familyId);

    return snapshot.map(value -> buildTrackedResponse(family, value))
      .orElseGet(() -> buildUnavailableResponse(family));
  }

  private IndexFamilyRuntimeStatusResponse buildTrackedResponse(IndexFamilyEntity family,
                                                               V2ReindexFamilyRuntimeSnapshot snapshot) {
    var familyId = family.getId();
    var consumerRunning = consumerManager.isConsumerRunning(familyId);
    var liveMetricsAvailable = consumerRunning || hasInProgressWork(snapshot) || !snapshot.isTerminal();
    var failure = buildFailure(snapshot);

    return new IndexFamilyRuntimeStatusResponse(
      familyId.toString(),
      family.getQueryVersion().getValue(),
      family.getStatus().getValue(),
      determineCurrentPhase(family.getStatus(), snapshot),
      toIso(snapshot.getUpdatedAt()),
      new Summary(
        asString(snapshot.getJobId()),
        true,
        liveMetricsAvailable,
        consumerRunning,
        nullableElapsed(snapshot.getCreatedAt(), snapshot.getDurationMs()),
        null,
        failure),
      new Details(
        new Phases(
          toPhaseBlock(snapshot.phase(V2ReindexPhaseType.STREAMING), V2ReindexPhaseType.STREAMING, familyId),
          toPhaseBlock(snapshot.phase(V2ReindexPhaseType.BROWSE_REBUILD), V2ReindexPhaseType.BROWSE_REBUILD, familyId),
          toPhaseBlock(snapshot.phase(V2ReindexPhaseType.CATCH_UP), V2ReindexPhaseType.CATCH_UP, familyId),
          toPhaseBlock(snapshot.phase(V2ReindexPhaseType.CUTOVER), V2ReindexPhaseType.CUTOVER, familyId)),
        new Resources(
          toResourceBlock(snapshot.resource(V2ReindexResourceType.INSTANCE)),
          toResourceBlock(snapshot.resource(V2ReindexResourceType.HOLDING)),
          toResourceBlock(snapshot.resource(V2ReindexResourceType.ITEM)))));
  }

  private IndexFamilyRuntimeStatusResponse buildUnavailableResponse(IndexFamilyEntity family) {
    return new IndexFamilyRuntimeStatusResponse(
      family.getId().toString(),
      family.getQueryVersion().getValue(),
      family.getStatus().getValue(),
      "UNAVAILABLE",
      null,
      new Summary(null, false, false, false, null, UNAVAILABLE_MESSAGE, null),
      null);
  }

  private Failure buildFailure(V2ReindexFamilyRuntimeSnapshot snapshot) {
    if (snapshot.getStatus() != V2ReindexRuntimeStatus.FAILED) {
      return null;
    }

    return new Failure(
      "RUNTIME_ERROR",
      snapshot.getErrorMessage(),
      findFailedPhase(snapshot),
      toIso(snapshot.getTerminalAt()));
  }

  private String determineCurrentPhase(IndexFamilyStatus familyStatus, V2ReindexFamilyRuntimeSnapshot snapshot) {
    if (familyStatus == IndexFamilyStatus.ACTIVE) {
      if (snapshot.phase(V2ReindexPhaseType.BROWSE_REBUILD).getStatus() == V2ReindexRuntimeStatus.IN_PROGRESS) {
        return "BROWSE_REBUILD";
      }
      return "ACTIVE";
    }
    if (familyStatus == IndexFamilyStatus.FAILED || snapshot.getStatus() == V2ReindexRuntimeStatus.FAILED) {
      return "FAILED";
    }
    if (familyStatus == IndexFamilyStatus.CUTTING_OVER
        || snapshot.phase(V2ReindexPhaseType.CUTOVER).getStatus() == V2ReindexRuntimeStatus.IN_PROGRESS) {
      return "CUTTING_OVER";
    }
    if (snapshot.phase(V2ReindexPhaseType.BROWSE_REBUILD).getStatus() == V2ReindexRuntimeStatus.IN_PROGRESS
        || familyStatus != IndexFamilyStatus.ACTIVE
        && snapshot.phase(V2ReindexPhaseType.STREAMING).getStatus() == V2ReindexRuntimeStatus.COMPLETED
        && snapshot.phase(V2ReindexPhaseType.BROWSE_REBUILD).getStatus() == V2ReindexRuntimeStatus.PENDING) {
      return "BROWSE_REBUILD";
    }
    if (snapshot.getStatus() == V2ReindexRuntimeStatus.COMPLETED) {
      return "ACTIVE";
    }
    if (snapshot.phase(V2ReindexPhaseType.STREAMING).getStatus() == V2ReindexRuntimeStatus.IN_PROGRESS) {
      return "STREAMING";
    }
    if (snapshot.phase(V2ReindexPhaseType.CATCH_UP).getStatus() == V2ReindexRuntimeStatus.COMPLETED
        && familyStatus == IndexFamilyStatus.STAGED) {
      return "READY_TO_SWITCH";
    }
    if (familyStatus == IndexFamilyStatus.STAGED
        || snapshot.phase(V2ReindexPhaseType.CATCH_UP).getStatus() == V2ReindexRuntimeStatus.IN_PROGRESS) {
      return "CATCHING_UP";
    }
    return familyStatus.getValue();
  }

  private String findFailedPhase(V2ReindexFamilyRuntimeSnapshot snapshot) {
    return snapshot.getPhases().entrySet().stream()
      .filter(entry -> entry.getValue().getStatus() == V2ReindexRuntimeStatus.FAILED)
      .map(entry -> phaseName(entry.getKey()))
      .findFirst()
      .orElse(null);
  }

  private PhaseBlock toPhaseBlock(V2ReindexPhaseRuntime runtime, V2ReindexPhaseType phaseType, UUID familyId) {
    var details = new LinkedHashMap<String, Object>(runtime.getDetails());
    if (phaseType == V2ReindexPhaseType.CATCH_UP) {
      enrichCatchUpDetails(familyId, runtime, details);
    }

    return new PhaseBlock(
      runtime.getStatus().name(),
      toIso(runtime.getStartedAt()),
      toIso(runtime.getEndedAt()),
      nullableElapsed(runtime.getStartedAt(), runtime.getDurationMs()),
      details);
  }

  private void enrichCatchUpDetails(UUID familyId, V2ReindexPhaseRuntime runtime, Map<String, Object> details) {
    var hasStagedSnapshot = consumerManager.hasStagedCutoverSnapshot(familyId);
    if (hasStagedSnapshot) {
      details.putAll(StagedCutoverSnapshotDetailsHelper.createBaseDetails(consumerManager, familyId));
    }

    var consumerRunning = consumerManager.isConsumerRunning(familyId);
    if (consumerRunning) {
      safeMetric(() -> StagedCutoverSnapshotDetailsHelper.applyLagDetails(details,
        consumerManager.getConsumerLag(familyId), null));
      safeMetric(() -> {
        var lagToTarget = hasStagedSnapshot
          ? consumerManager.getConsumerLagToStagedCutoverSnapshot(familyId)
          : consumerManager.getConsumerLagToTarget(familyId);
        StagedCutoverSnapshotDetailsHelper.applyLagDetails(details, null, lagToTarget);
        if (!hasStagedSnapshot) {
          details.put("readyForSwitchOver", false);
        }
        if (lagToTarget == 0L && runtimeStatusTracker != null) {
          runtimeStatusTracker.markCatchUpReady(familyId);
        }
      });
      if (!hasStagedSnapshot) {
        safeMetric(() -> {
          var partitions = consumerManager.getTrackedPartitionCount(familyId);
          details.put("partitions", partitions);
          details.putIfAbsent("targetPartitions", partitions);
        });
      }
      var catchUpRuntime = runtime;
      if (runtime.getFirstReadyAt() == null && runtimeStatusTracker != null) {
        catchUpRuntime = runtimeStatusTracker.find(familyId)
          .map(snapshot -> snapshot.phase(V2ReindexPhaseType.CATCH_UP))
          .orElse(runtime);
      }
      putCatchUpTiming(details, catchUpRuntime);
      return;
    }

    if (runtime.getStatus() == V2ReindexRuntimeStatus.COMPLETED) {
      StagedCutoverSnapshotDetailsHelper.applyLagDetails(details, 0L, 0L);
    } else if (runtime.getLastObservedValue() != null) {
      StagedCutoverSnapshotDetailsHelper.applyLagDetails(details, null, runtime.getLastObservedValue());
    }
    details.putIfAbsent("readyForSwitchOver", false);
    putCatchUpTiming(details, runtime);
  }

  private void putCatchUpTiming(Map<String, Object> details, V2ReindexPhaseRuntime runtime) {
    if (runtime.getFirstReadyAt() == null || runtime.getStartedAt() == null) {
      return;
    }

    details.putIfAbsent("lagReachedZeroAt", toIso(runtime.getFirstReadyAt()));
    details.putIfAbsent("timeUntilLagZeroMs",
      Math.max(runtime.getFirstReadyAt().toEpochMilli() - runtime.getStartedAt().toEpochMilli(), 0L));

    var waitEnd = runtime.getEndedAt() != null ? runtime.getEndedAt() : Instant.now();
    details.putIfAbsent("timeWaitingForManualSwitchMs",
      Math.max(waitEnd.toEpochMilli() - runtime.getFirstReadyAt().toEpochMilli(), 0L));
  }

  private ResourceBlock toResourceBlock(V2ReindexResourceRuntime runtime) {
    return new ResourceBlock(
      runtime.getStatus().name(),
      toIso(runtime.getStartedAt()),
      toIso(runtime.getEndedAt()),
      nullableElapsed(runtime.getStartedAt(), runtime.getDurationMs()),
      runtime.getRecordsProcessed(),
      runtime.getFailedBatches(),
      runtime.getBatchesProcessed(),
      runtime.getTotalBatchElapsedMs(),
      runtime.getTotalEnrichMs(),
      runtime.getTotalConvertMs(),
      runtime.getTotalOsBulkMs(),
      average(runtime.getTotalBatchElapsedMs(), runtime.getBatchesProcessed()),
      average(runtime.getRecordsProcessed(), runtime.getBatchesProcessed()));
  }

  private void safeMetric(Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      log.warn("safeMetric:: failed to load live runtime metric [message: {}]", e.getMessage());
    }
  }

  private Double average(long numerator, long denominator) {
    return denominator > 0 ? (double) numerator / denominator : null;
  }

  private boolean hasInProgressWork(V2ReindexFamilyRuntimeSnapshot snapshot) {
    return snapshot.getPhases().values().stream()
      .anyMatch(runtime -> runtime.getStatus() == V2ReindexRuntimeStatus.IN_PROGRESS)
      || snapshot.getResources().values().stream()
      .anyMatch(runtime -> runtime.getStatus() == V2ReindexRuntimeStatus.IN_PROGRESS);
  }

  private Long nullableElapsed(Instant startedAt, long durationMs) {
    return startedAt != null ? durationMs : null;
  }

  private String toIso(Instant value) {
    return value != null ? value.toString() : null;
  }

  private String asString(UUID value) {
    return value != null ? value.toString() : null;
  }

  private String phaseName(V2ReindexPhaseType phaseType) {
    return switch (phaseType) {
      case STREAMING -> "STREAMING";
      case BROWSE_REBUILD -> "BROWSE_REBUILD";
      case CATCH_UP -> "CATCHING_UP";
      case CUTOVER -> "CUTTING_OVER";
    };
  }

  public record IndexFamilyRuntimeStatusResponse(String familyId,
                                                 String queryVersion,
                                                 String familyStatus,
                                                 String currentPhase,
                                                 String updatedAt,
                                                 Summary summary,
                                                 Details details) {
  }

  public record Summary(String jobId,
                        boolean trackedInMemory,
                        boolean liveMetricsAvailable,
                        boolean temporaryConsumerRunning,
                        Long overallElapsedMs,
                        String message,
                        Failure failure) {
  }

  public record Failure(String type, String message, String phase, String occurredAt) {
  }

  public record Details(Phases phases, Resources resources) {
  }

  public record Phases(PhaseBlock streaming,
                       PhaseBlock browseRebuild,
                       PhaseBlock catchUp,
                       PhaseBlock cutover) {
  }

  public record PhaseBlock(String status,
                           String startedAt,
                           String completedAt,
                           Long elapsedMs,
                           Map<String, Object> details) {
  }

  public record Resources(ResourceBlock instance,
                          ResourceBlock holding,
                          ResourceBlock item) {
  }

  public record ResourceBlock(String status,
                              String startedAt,
                              String completedAt,
                              Long elapsedMs,
                              long processedRecords,
                              long failedBatches,
                              long batchCount,
                              long totalBatchElapsedMs,
                              long totalEnrichMs,
                              long totalConvertMs,
                              long totalOsBulkMs,
                              Double avgBatchElapsedMs,
                              Double avgDocsPerBatch) {
  }
}
