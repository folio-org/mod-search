package org.folio.search.service.systemuser;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.FolioEnvironment;
import org.folio.search.configuration.properties.FolioSystemUserProperties;
import org.folio.search.model.SystemUser;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class SystemUserService {
  public static final String SYSTEM_USER_CACHE = "system-user-cache";

  private final PrepareSystemUserService prepareSystemUserService;
  private final FolioSystemUserProperties folioSystemUserConf;
  private final FolioEnvironment folioEnvironment;

  public void prepareSystemUser() {
    log.info("Preparing system user...");

    prepareSystemUserService.setupSystemUser();

    log.info("System user has been created");
  }

  @Cacheable(cacheNames = SYSTEM_USER_CACHE, sync = true)
  public SystemUser getSystemUser(String tenantId) {
    log.info("Attempting to issue token for system user [tenantId={}]", tenantId);
    var systemUser = SystemUser.builder()
      .tenantId(tenantId)
      .username(folioSystemUserConf.getUsername())
      .okapiUrl(folioEnvironment.getOkapiUrl())
      .build();

    var token = prepareSystemUserService.loginSystemUser(systemUser);

    log.info("Token for system user has been issued [tenantId={}]", tenantId);
    return systemUser.withToken(token);
  }
}
