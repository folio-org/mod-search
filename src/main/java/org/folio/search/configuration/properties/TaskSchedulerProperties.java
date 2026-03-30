package org.folio.search.configuration.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties("folio.task-scheduler")
public class TaskSchedulerProperties {

  /**
   * The delay in milliseconds before the task scheduler starts executing tasks.
   */
  private long delayMs = 70000L;

  /**
   * The size of the thread pool for task scheduling.
   */
  private int poolSize = 2;

  /**
   * The prefix for the names of threads in the task scheduler.
   */
  private String threadNamePrefix = "mod-search-task-scheduler-";
}
