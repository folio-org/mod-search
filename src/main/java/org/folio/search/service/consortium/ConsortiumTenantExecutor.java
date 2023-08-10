package org.folio.search.service.consortium;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.tools.systemuser.SystemUserScopedExecutionService;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ConsortiumTenantExecutor {

  private final FolioExecutionContext folioExecutionContext;
  private final TenantProvider tenantProvider;
  private final SystemUserScopedExecutionService scopedExecutionService;

  public <T> T execute(Supplier<T> operation) {
    var contextTenantId = folioExecutionContext.getTenantId();
    return execute(contextTenantId, operation);
  }

  public <T> T execute(String originalTenantId, Supplier<T> operation) {
    var tenantId = tenantProvider.getTenant(originalTenantId);
    log.info("Changing context from {} to {}", originalTenantId, tenantId);
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
