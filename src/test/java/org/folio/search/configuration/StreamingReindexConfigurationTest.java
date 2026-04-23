package org.folio.search.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

@UnitTest
class StreamingReindexConfigurationTest {

  private final StreamingReindexConfiguration configuration = new StreamingReindexConfiguration();

  @Test
  void streamingReindexInstanceBatchExecutor_appliesContextDecoratorAndCallerRunsPolicy() {
    var executor = (ThreadPoolTaskExecutor) configuration.streamingReindexInstanceBatchExecutor(3);

    try {
      assertThat(executor.getCorePoolSize()).isEqualTo(3);
      assertThat(executor.getMaxPoolSize()).isEqualTo(3);
      assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isZero();
      assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler()).isInstanceOf(CallerRunsPolicy.class);
      assertThat(ReflectionTestUtils.getField(executor, "taskDecorator")).isNotNull();
    } finally {
      executor.destroy();
    }
  }
}
