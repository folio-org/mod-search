package org.folio.search.configuration;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class StreamingReindexConfiguration {

  @Bean("streamingReindexExecutor")
  public Executor streamingReindexExecutor(
    @Value("${folio.streaming-reindex.executor-core-pool-size:2}") int corePoolSize,
    @Value("${folio.streaming-reindex.executor-max-pool-size:4}") int maxPoolSize) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(10);
    executor.setThreadNamePrefix("streaming-reindex-");
    executor.initialize();
    return executor;
  }

  @Bean("browseUpsertExecutor")
  public Executor browseUpsertExecutor(
    @Value("${folio.browse-upsert.executor-core-pool-size:3}") int corePoolSize,
    @Value("${folio.browse-upsert.executor-max-pool-size:4}") int maxPoolSize) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(10);
    executor.setThreadNamePrefix("browse-upsert-");
    executor.initialize();
    return executor;
  }

  @Bean("v2BrowseRebuildExecutor")
  public Executor v2BrowseRebuildExecutor(
    @Value("${folio.v2-browse-rebuild.executor-core-pool-size:1}") int corePoolSize,
    @Value("${folio.v2-browse-rebuild.executor-max-pool-size:1}") int maxPoolSize,
    @Value("${folio.v2-browse-rebuild.executor-queue-capacity:10}") int queueCapacity) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("v2-browse-rebuild-");
    executor.initialize();
    return executor;
  }
}
