package org.folio.search.service.consortium;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.tools.systemuser.SystemUserScopedExecutionService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConsortiaTenantExecutor {

  private final FolioExecutionContext folioExecutionContext;
  private final TenantProvider tenantProvider;
  private final SystemUserScopedExecutionService scopedExecutionService;

  public <T> T execute(Supplier<T> operation) {
    var contextTenantId = folioExecutionContext.getTenantId();
    return execute(contextTenantId, operation);
  }

  public <T> T execute(String originalTenantId, Supplier<T> operation) {
    var tenantId = tenantProvider.getTenant(originalTenantId);
    if (originalTenantId.equals(tenantId)) {
      return operation.get();
    } else {
      return scopedExecutionService.executeSystemUserScoped(tenantId, operation::get);
    }
  }

  public void run(Runnable operation) {
    execute(() -> {
      operation.run();
      return null;
    });
  }

}
