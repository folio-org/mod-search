package org.folio.search.configuration;

import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.search.service.FolioExecutor;
import org.folio.spring.scope.FolioExecutionScopeExecutionContextManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@EnableAsync
@Configuration
@RequiredArgsConstructor
public class AsyncConfig {

  private final StreamIdsProperties streamIdsProperties;

  @Bean("streamIdsExecutor")
  public Executor streamIdsExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(streamIdsProperties.getCorePoolSize());
    executor.setMaxPoolSize(streamIdsProperties.getMaxPoolSize());
    executor.setQueueCapacity(streamIdsProperties.getQueueCapacity());
    executor.setThreadNamePrefix("StreamResourceIds-");
    executor.setTaskDecorator(FolioExecutionScopeExecutionContextManager::getRunnableWithCurrentFolioContext);
    executor.initialize();
    return executor;
  }

  @Bean("reindexExecutor")
  public FolioExecutor remappingExecutor() {
    return new FolioExecutor(0, 1);
  }
}

