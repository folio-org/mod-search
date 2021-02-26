package org.folio.search.service;

import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.beginFolioExecutionContext;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.endFolioExecutionContext;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.folio.search.service.context.SystemUserFolioExecutionContext;
import org.folio.search.service.systemuser.SystemUserService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantScopedExecutionService {
  private final FolioModuleMetadata moduleMetadata;
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
  public <T> T executeTenantScoped(String tenantId, ThrowableSupplier<T> job) {
    try {
      beginFolioExecutionContext(folioExecutionContext(tenantId));
      return job.get();
    } finally {
      endFolioExecutionContext();
    }
  }

  private FolioExecutionContext folioExecutionContext(String tenant) {
    return new SystemUserFolioExecutionContext(
      systemUserService.getSystemUserParameters(tenant), moduleMetadata);
  }

  @FunctionalInterface
  public interface ThrowableSupplier<T> {
    T get() throws Exception;
  }
}
