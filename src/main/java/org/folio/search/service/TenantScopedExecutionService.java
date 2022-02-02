package org.folio.search.service;

import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.beginFolioExecutionContext;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.endFolioExecutionContext;

import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.folio.search.model.context.FolioExecutionContextBuilder;
import org.folio.search.service.systemuser.SystemUserService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantScopedExecutionService {
  private final FolioExecutionContextBuilder contextBuilder;
  private final SystemUserService systemUserService;

  /**
   * Executes given job tenant scoped.
   *
   * @param tenantId - The tenant name.
   * @param job      - Job to be executed in tenant scope.
   * @param <T>      - Optional return value for the job.
   * @return Result of job.
   * @throws RuntimeException - Wrapped exception from the job.
   */
  @SneakyThrows
  public <T> T executeTenantScoped(String tenantId, Callable<T> job) {
    try {
      beginFolioExecutionContext(folioExecutionContext(tenantId));
      return job.call();
    } finally {
      endFolioExecutionContext();
    }
  }

  private FolioExecutionContext folioExecutionContext(String tenant) {
    return contextBuilder.forSystemUser(systemUserService.getSystemUser(tenant));
  }
}
