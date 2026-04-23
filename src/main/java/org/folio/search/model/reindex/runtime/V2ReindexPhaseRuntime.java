package org.folio.search.model.reindex.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class V2ReindexPhaseRuntime {

  private V2ReindexRuntimeStatus status = V2ReindexRuntimeStatus.PENDING;
  private Instant startedAt;
  private Instant updatedAt;
  private Instant endedAt;
  private long totalSteps;
  private long completedSteps;
  private Long lastObservedValue;
  private long durationMs;
  private String errorMessage;
  private final Map<String, Object> details = new LinkedHashMap<>();

  public void start(Instant now) {
    if (startedAt == null) {
      startedAt = now;
    }
    status = V2ReindexRuntimeStatus.IN_PROGRESS;
    updatedAt = now;
    refreshDuration(now);
  }

  public void advance(long steps, Long observedValue, Instant now) {
    if (startedAt == null) {
      startedAt = now;
    }
    status = V2ReindexRuntimeStatus.IN_PROGRESS;
    completedSteps += Math.max(steps, 0L);
    if (observedValue != null) {
      lastObservedValue = observedValue;
    }
    updatedAt = now;
    refreshDuration(now);
  }

  public void complete(Instant now) {
    if (startedAt == null) {
      startedAt = now;
    }
    status = V2ReindexRuntimeStatus.COMPLETED;
    endedAt = now;
    updatedAt = now;
    refreshDuration(now);
  }

  public void fail(String message, Instant now) {
    if (startedAt == null) {
      startedAt = now;
    }
    status = V2ReindexRuntimeStatus.FAILED;
    errorMessage = message;
    endedAt = now;
    updatedAt = now;
    refreshDuration(now);
  }

  public void totalSteps(long totalSteps) {
    this.totalSteps = Math.max(totalSteps, 0L);
  }

  public void setDetails(Map<String, Object> values) {
    details.clear();
    if (values != null) {
      details.putAll(values);
    }
  }

  public void putDetail(String key, Object value) {
    if (key != null) {
      details.put(key, value);
    }
  }

  public V2ReindexPhaseRuntime copy() {
    var copy = new V2ReindexPhaseRuntime();
    copy.status = status;
    copy.startedAt = startedAt;
    copy.updatedAt = updatedAt;
    copy.endedAt = endedAt;
    copy.totalSteps = totalSteps;
    copy.completedSteps = completedSteps;
    copy.lastObservedValue = lastObservedValue;
    copy.durationMs = durationMs;
    copy.errorMessage = errorMessage;
    copy.details.putAll(details);
    return copy;
  }

  private void refreshDuration(Instant now) {
    if (startedAt == null) {
      durationMs = 0L;
      return;
    }
    var end = endedAt != null ? endedAt : now;
    durationMs = Duration.between(startedAt, end).toMillis();
  }
}
