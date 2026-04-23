package org.folio.search.model.reindex.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class V2ReindexResourceRuntimeTest {

  @Test
  void consortiumResourceRunsKeepAccumulatingDurationAcrossTenantBoundaries() {
    var runtime = new V2ReindexResourceRuntime();
    var startedAt = Instant.parse("2026-04-22T10:00:00Z");
    var firstCompletedAt = Instant.parse("2026-04-22T10:00:10Z");
    var secondTenantStartedAt = Instant.parse("2026-04-22T10:00:20Z");
    var secondBatchAt = Instant.parse("2026-04-22T10:00:30Z");

    runtime.start(startedAt);
    runtime.recordBatch(10, 15L, 4L, 3L, 2L, startedAt.plusSeconds(5));
    runtime.complete(firstCompletedAt);

    runtime.start(secondTenantStartedAt);
    runtime.recordBatch(20, 25L, 6L, 5L, 4L, secondBatchAt);

    assertThat(runtime.getDurationMs()).isEqualTo(30_000L);
    assertThat(runtime.getEndedAt()).isNull();

    var secondCompletedAt = Instant.parse("2026-04-22T10:00:40Z");
    runtime.complete(secondCompletedAt);

    assertThat(runtime.getDurationMs()).isEqualTo(40_000L);
    assertThat(runtime.getEndedAt()).isEqualTo(secondCompletedAt);
    assertThat(runtime.getRecordsProcessed()).isEqualTo(30L);
    assertThat(runtime.getBatchesProcessed()).isEqualTo(2L);
    assertThat(runtime.getStatus()).isEqualTo(V2ReindexRuntimeStatus.COMPLETED);
  }
}
