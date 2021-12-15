package org.folio.search.service;

import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.beginFolioExecutionContext;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.endFolioExecutionContext;

import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.folio.search.configuration.properties.ModuleConfigurationProperties;
import org.folio.search.service.context.SystemFolioExecutionContext;
import org.folio.search.service.systemuser.ModuleUserProvider;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantScopedExecutionService {

  private final ModuleUserProvider moduleUserProvider;
  private final FolioModuleMetadata folioModuleMetadata;
  private final ModuleConfigurationProperties moduleConfiguration;

  /**
   * Executes given job tenant scoped.
   *
   * @param tenantId - The tenant name.
   * @param job - Job to be executed in tenant scope.
   * @param <T> - Optional return value for the job.
   * @return Result of job.
   * @throws RuntimeException - Wrapped exception from the job.
   */
  @SneakyThrows
  public <T> T executeTenantScoped(String tenantId, Callable<T> job) {
    try {
      beginFolioExecutionContext(buildFolioExecutionContext(tenantId));
      return job.call();
    } finally {
      endFolioExecutionContext();
    }
  }

  private FolioExecutionContext buildFolioExecutionContext(String tenantId) {
    return SystemFolioExecutionContext.builder()
      .tenantId(tenantId)
      .token(moduleUserProvider.getOkapiToken(tenantId))
      .okapiUrl(moduleConfiguration.getOkapiUrl())
      .userName(moduleConfiguration.getModuleName())
      .folioModuleMetadata(folioModuleMetadata)
      .build();
  }
}
