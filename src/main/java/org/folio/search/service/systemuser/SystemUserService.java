package org.folio.search.service.systemuser;

import static org.folio.search.configuration.SearchCacheNames.SYSTEM_USER_CACHE;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.FolioSystemUserProperties;
import org.folio.search.configuration.properties.OkapiConfigurationProperties;
import org.folio.search.model.SystemUser;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class SystemUserService {

  private final PrepareSystemUserService prepareSystemUserService;
  private final FolioSystemUserProperties folioSystemUserConf;
  private final OkapiConfigurationProperties okapiProperties;

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
      .okapiUrl(okapiProperties.getOkapiUrl())
      .build();

    var token = prepareSystemUserService.loginSystemUser(systemUser);

    log.info("Token for system user has been issued [tenantId={}]", tenantId);
    return systemUser.withToken(token);
  }
}
