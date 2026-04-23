package org.folio.search.model.reindex.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.search.model.types.IndexFamilyStatus;
import org.folio.search.model.types.QueryVersion;

@Data
@NoArgsConstructor
public class V2ReindexFamilyRuntimeSnapshot {

  private UUID familyId;
  private UUID jobId;
  private String tenantId;
  private QueryVersion queryVersion;
  private IndexFamilyStatus familyStatus;
  private V2ReindexRuntimeStatus status = V2ReindexRuntimeStatus.PENDING;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant terminalAt;
  private long durationMs;
  private String errorMessage;
  private final Map<V2ReindexPhaseType, V2ReindexPhaseRuntime> phases =
    new EnumMap<>(V2ReindexPhaseType.class);
  private final Map<V2ReindexResourceType, V2ReindexResourceRuntime> resources =
    new EnumMap<>(V2ReindexResourceType.class);

  public void reset(UUID familyId, UUID jobId, String tenantId, QueryVersion queryVersion, Instant now) {
    this.familyId = familyId;
    this.jobId = jobId;
    this.tenantId = tenantId;
    this.queryVersion = queryVersion;
    this.familyStatus = IndexFamilyStatus.BUILDING;
    this.status = V2ReindexRuntimeStatus.IN_PROGRESS;
    this.createdAt = now;
    this.updatedAt = now;
    this.terminalAt = null;
    this.durationMs = 0L;
    this.errorMessage = null;
    phases.clear();
    resources.clear();
    for (var phaseType : V2ReindexPhaseType.values()) {
      phases.put(phaseType, new V2ReindexPhaseRuntime());
    }
    for (var resourceType : V2ReindexResourceType.values()) {
      resources.put(resourceType, new V2ReindexResourceRuntime());
    }
  }

  public boolean isTerminal() {
    return status.isTerminal();
  }

  public void touch(Instant now) {
    updatedAt = now;
    refreshDuration(now);
  }

  public void markTerminal(V2ReindexRuntimeStatus terminalStatus, Instant now, String errorMessage) {
    status = terminalStatus;
    terminalAt = now;
    updatedAt = now;
    this.errorMessage = errorMessage;
    refreshDuration(now);
  }

  public void resume(IndexFamilyStatus familyStatus, Instant now) {
    status = V2ReindexRuntimeStatus.IN_PROGRESS;
    terminalAt = null;
    errorMessage = null;
    this.familyStatus = familyStatus;
    updatedAt = now;
    refreshDuration(now);
  }

  public void updateFamilyStatus(IndexFamilyStatus familyStatus, Instant now) {
    this.familyStatus = familyStatus;
    touch(now);
  }

  public V2ReindexPhaseRuntime phase(V2ReindexPhaseType phaseType) {
    return phases.computeIfAbsent(phaseType, ignored -> new V2ReindexPhaseRuntime());
  }

  public V2ReindexResourceRuntime resource(V2ReindexResourceType resourceType) {
    return resources.computeIfAbsent(resourceType, ignored -> new V2ReindexResourceRuntime());
  }

  public V2ReindexFamilyRuntimeSnapshot copy() {
    var copy = new V2ReindexFamilyRuntimeSnapshot();
    copy.familyId = familyId;
    copy.jobId = jobId;
    copy.tenantId = tenantId;
    copy.queryVersion = queryVersion;
    copy.familyStatus = familyStatus;
    copy.status = status;
    copy.createdAt = createdAt;
    copy.updatedAt = updatedAt;
    copy.terminalAt = terminalAt;
    copy.durationMs = durationMs;
    copy.errorMessage = errorMessage;
    phases.forEach((phaseType, runtime) -> copy.phases.put(phaseType, runtime.copy()));
    resources.forEach((resourceType, runtime) -> copy.resources.put(resourceType, runtime.copy()));
    return copy;
  }

  private void refreshDuration(Instant now) {
    if (createdAt == null) {
      durationMs = 0L;
      return;
    }
    var end = terminalAt != null ? terminalAt : now;
    durationMs = Duration.between(createdAt, end).toMillis();
  }
}
