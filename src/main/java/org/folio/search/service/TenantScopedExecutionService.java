package org.folio.search.service;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.ScopeExecutionException;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.config.properties.FolioEnvironment;
import org.folio.spring.context.ExecutionContextBuilder;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@Primary
public class TenantScopedExecutionService extends SystemUserScopedExecutionService {

  private final FolioExecutionContext executionContext;
  private final TenantScopedExecutionContextBuilder contextBuilder;

  public TenantScopedExecutionService(FolioExecutionContext executionContext,
                                      ExecutionContextBuilder contextBuilder) {
    super(executionContext, contextBuilder);
    this.executionContext = executionContext;
    this.contextBuilder = (TenantScopedExecutionContextBuilder) contextBuilder;
  }

  public <T> T executeTenantScoped(String tenantId, Callable<T> action) {
    Map<String, Collection<String>> headers = executionContext == null ? emptyMap() : executionContext.getAllHeaders();
    try (var fex = new FolioExecutionContextSetter(contextBuilder.buildContext(tenantId, headers))) {
      log.info("Executing tenant scoped action [tenant={}]", tenantId);
      return action.call();
    } catch (Exception e) {
      log.error("Failed to execute tenant scoped action", e);
      throw new ScopeExecutionException(e);
    }
  }

  @Primary
  @Component
  protected static class TenantScopedExecutionContextBuilder extends ExecutionContextBuilder {

    private final FolioModuleMetadata moduleMetadata;

    TenantScopedExecutionContextBuilder(FolioEnvironment folioEnvironment,
                                        FolioModuleMetadata moduleMetadata) {
      super(folioEnvironment, moduleMetadata);
      this.moduleMetadata = moduleMetadata;
    }

    public FolioExecutionContext buildContext(String tenantId, Map<String, Collection<String>> headers) {
      Map<String, Collection<String>> newHeaders = new HashMap<>(headers);
      if (isNotBlank(tenantId)) {
        newHeaders.put(XOkapiHeaders.TENANT, singleton(tenantId));
      }
      return new DefaultFolioExecutionContext(moduleMetadata, newHeaders);
    }

  }
}
