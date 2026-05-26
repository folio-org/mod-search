package org.folio.search.service;

import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.getRunnableWithCurrentFolioContext;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.NonNull;

/**
 * Provides async execution decorated with FolioExecutionContext using virtual threads.
 */
public class FolioExecutor implements ExecutorService {

  private final ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();

  @Override
  public void execute(@NonNull Runnable command) {
    delegate.execute(getRunnableWithCurrentFolioContext(command));
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  @Override
  public @NonNull List<Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  @Override
  public @NonNull <T> Future<T> submit(@NonNull Callable<T> task) {
    return delegate.submit(task);
  }

  @Override
  public @NonNull <T> Future<T> submit(@NonNull Runnable task, T result) {
    return delegate.submit(getRunnableWithCurrentFolioContext(task), result);
  }

  @Override
  public @NonNull Future<?> submit(@NonNull Runnable task) {
    return delegate.submit(getRunnableWithCurrentFolioContext(task));
  }

  @Override
  public @NonNull <T> List<Future<T>> invokeAll(@NonNull Collection<? extends Callable<T>> tasks)
    throws InterruptedException {
    return delegate.invokeAll(tasks);
  }

  @Override
  public @NonNull <T> List<Future<T>> invokeAll(@NonNull Collection<? extends Callable<T>> tasks, long timeout,
                                                @NonNull TimeUnit unit) throws InterruptedException {
    return delegate.invokeAll(tasks, timeout, unit);
  }

  @Override
  public @NonNull <T> T invokeAny(@NonNull Collection<? extends Callable<T>> tasks)
    throws InterruptedException, ExecutionException {
    return delegate.invokeAny(tasks);
  }

  @Override
  public @NonNull <T> T invokeAny(@NonNull Collection<? extends Callable<T>> tasks, long timeout,
                                  @NonNull TimeUnit unit) throws InterruptedException, ExecutionException,
    TimeoutException {
    return delegate.invokeAny(tasks, timeout, unit);
  }
}
