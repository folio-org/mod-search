package org.folio.search.configuration;

import lombok.RequiredArgsConstructor;
import org.folio.search.configuration.properties.TaskSchedulerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@RequiredArgsConstructor
public class TaskSchedulerConfiguration {

  @Bean
  public ThreadPoolTaskScheduler instanceSharingCompleteTaskScheduler(TaskSchedulerProperties properties) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(properties.getPoolSize());
    scheduler.setThreadNamePrefix(properties.getThreadNamePrefix());
    scheduler.initialize();
    return scheduler;
  }
}
