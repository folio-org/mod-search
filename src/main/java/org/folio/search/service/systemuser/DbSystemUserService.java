package org.folio.search.service.systemuser;

import static org.folio.search.service.systemuser.InMemorySystemUserService.SYSTEM_USER_CACHE;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.beginFolioExecutionContext;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.endFolioExecutionContext;

import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.FolioEnvironment;
import org.folio.search.configuration.properties.FolioSystemUserProperties;
import org.folio.search.model.SystemUser;
import org.folio.search.repository.SystemUserRepository;
import org.folio.search.service.context.FolioExecutionContextBuilder;
import org.folio.spring.FolioExecutionContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Log4j2
@RequiredArgsConstructor
@Service
@ConditionalOnMissingBean(FolioEnvironment.class)
public class DbSystemUserService implements SystemUserService {
  private final PrepareSystemUserService prepareSystemUserService;
  private final SystemUserRepository systemUserRepository;
  private final FolioExecutionContextBuilder contextBuilder;
  private final FolioSystemUserProperties folioSystemUserConf;

  @Override
  public void prepareSystemUser(FolioExecutionContext context) {
    log.info("Preparing system user...");

    var systemUser = prepareSystemUserService.setupSystemUser(context);
    systemUserRepository.save(systemUser);

    log.info("System user has been created");
  }

  @Cacheable(cacheNames = SYSTEM_USER_CACHE, sync = true)
  @Override
  public SystemUser getSystemUser(String tenantId) {
    log.info("Attempting to issue token for system user [tenantId={}]", tenantId);

    var systemUser = findSystemUser(tenantId)
      .orElseThrow(() -> new IllegalStateException("There is no system user configured"));

    var token = prepareSystemUserService.loginSystemUser(systemUser);

    log.info("Token for system user has been issued [tenantId={}]", tenantId);
    return systemUser.withToken(token);
  }

  private Optional<SystemUser> findSystemUser(String tenantId) {
    return executeTenantScoped(contextBuilder.dbOnlyContext(tenantId),
      () -> systemUserRepository.findOneByUsername(folioSystemUserConf.getUsername()));
  }

  private <T> T executeTenantScoped(FolioExecutionContext context, Supplier<T> job) {
    try {
      beginFolioExecutionContext(context);
      return job.get();
    } finally {
      endFolioExecutionContext();
    }
  }
}
