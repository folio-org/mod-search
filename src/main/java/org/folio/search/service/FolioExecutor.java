package org.folio.search.service;

import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.getRunnableWithCurrentFolioContext;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NonNull;

/**
 * Provides async execution decorated with FolioExecutionContext.
 *
 */
public class FolioExecutor extends ThreadPoolExecutor {
  public FolioExecutor(int corePoolSize, int maximumPoolSize) {
    super(corePoolSize, maximumPoolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
      Thread.ofVirtual().factory());
  }

  @Override
  public void execute(@NonNull Runnable command) {
    super.execute(getRunnableWithCurrentFolioContext(command));
  }
}
