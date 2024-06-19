package org.folio.search.service;

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

  private final ExecutionContextBuilder contextBuilder;

  public TenantScopedExecutionService(FolioExecutionContext executionContext,
                                      ExecutionContextBuilder contextBuilder) {
    super(executionContext, contextBuilder);
    this.contextBuilder = contextBuilder;
  }

  public <T> T executeTenantScoped(String tenantId, Callable<T> action) {
    try (var fex = new FolioExecutionContextSetter(contextBuilder.buildContext(tenantId))) {
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

    private final FolioExecutionContext executionContext;

    TenantScopedExecutionContextBuilder(FolioEnvironment folioEnvironment,
                                        FolioModuleMetadata moduleMetadata,
                                        FolioExecutionContext executionContext) {
      super(folioEnvironment, moduleMetadata);
      this.executionContext = executionContext;
    }

    @Override
    public FolioExecutionContext buildContext(String tenantId) {
      return buildContextWithTenant(tenantId);
    }

    private FolioExecutionContext buildContextWithTenant(String tenantId) {
      Map<String, Collection<String>> headers = new HashMap<>();
      if (isNotBlank(tenantId)) {
        headers.put(XOkapiHeaders.TENANT, singleton(tenantId));
      }
      var okapiUrl = executionContext.getOkapiUrl();
      if (isNotBlank(okapiUrl)) {
        headers.put(XOkapiHeaders.URL, singleton(okapiUrl));
      }
      var token = executionContext.getToken();
      if (isNotBlank(token)) {
        headers.put(XOkapiHeaders.TOKEN, singleton(token));
      }
      var userId = executionContext.getUserId() == null ? "" : executionContext.getUserId().toString();
      if (isNotBlank(userId)) {
        headers.put(XOkapiHeaders.USER_ID, singleton(userId));
      }
      var requestId = executionContext.getRequestId();
      if (isNotBlank(requestId)) {
        headers.put(XOkapiHeaders.REQUEST_ID, singleton(requestId));
      }
      return new DefaultFolioExecutionContext(executionContext.getFolioModuleMetadata(), headers);
    }
  }
}
