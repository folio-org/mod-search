package org.folio.search.model.reindex.runtime;

import java.time.Duration;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class V2ReindexResourceRuntime {

  private V2ReindexRuntimeStatus status = V2ReindexRuntimeStatus.PENDING;
  private Instant startedAt;
  private Instant updatedAt;
  private Instant endedAt;
  private long recordsProcessed;
  private long batchesProcessed;
  private long failedBatches;
  private int lastBatchSize;
  private long lastBatchMs;
  private long totalBatchElapsedMs;
  private long totalEnrichMs;
  private long totalConvertMs;
  private long totalOsBulkMs;
  private long durationMs;
  private String errorMessage;

  public void start(Instant now) {
    beginWork(now);
  }

  public void recordBatch(int batchSize, long batchMs, long enrichMs, long convertMs, long osBulkMs, Instant now) {
    beginWork(now);
    recordsProcessed += Math.max(batchSize, 0);
    batchesProcessed++;
    lastBatchSize = batchSize;
    lastBatchMs = batchMs;
    totalBatchElapsedMs += Math.max(batchMs, 0L);
    totalEnrichMs += Math.max(enrichMs, 0L);
    totalConvertMs += Math.max(convertMs, 0L);
    totalOsBulkMs += Math.max(osBulkMs, 0L);
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
    failedBatches++;
    errorMessage = message;
    endedAt = now;
    updatedAt = now;
    refreshDuration(now);
  }

  public V2ReindexResourceRuntime copy() {
    var copy = new V2ReindexResourceRuntime();
    copy.status = status;
    copy.startedAt = startedAt;
    copy.updatedAt = updatedAt;
    copy.endedAt = endedAt;
    copy.recordsProcessed = recordsProcessed;
    copy.batchesProcessed = batchesProcessed;
    copy.failedBatches = failedBatches;
    copy.lastBatchSize = lastBatchSize;
    copy.lastBatchMs = lastBatchMs;
    copy.totalBatchElapsedMs = totalBatchElapsedMs;
    copy.totalEnrichMs = totalEnrichMs;
    copy.totalConvertMs = totalConvertMs;
    copy.totalOsBulkMs = totalOsBulkMs;
    copy.durationMs = durationMs;
    copy.errorMessage = errorMessage;
    return copy;
  }

  private void beginWork(Instant now) {
    if (startedAt == null) {
      startedAt = now;
    }
    status = V2ReindexRuntimeStatus.IN_PROGRESS;
    endedAt = null;
    errorMessage = null;
    updatedAt = now;
    refreshDuration(now);
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
