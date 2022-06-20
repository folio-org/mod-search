package org.folio.search.service.systemuser;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.configuration.SearchCacheNames.SYSTEM_USER_CACHE;

import org.folio.search.model.SystemUser;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

@IntegrationTest
class SystemUserServiceIT extends BaseIntegrationTest {

  @Autowired
  private SystemUserService systemUserService;
  @Autowired
  private CacheManager cacheManager;

  @Test
  void shouldCreateSystemUserDuringTenantInit() {
    var tenantId = "should_create_system_user";

    setUpTenant(tenantId);

    var wireMockServer = getWireMock();
    wireMockServer.verify(getRequestedFor(urlEqualTo("/users?query=username%3D%3Dmod-search")));
    wireMockServer.verify(postRequestedFor(urlEqualTo("/users")));
    wireMockServer.verify(postRequestedFor(urlEqualTo("/authn/credentials")));
    wireMockServer.verify(postRequestedFor(urlEqualTo("/perms/users")));

    var systemUser = systemUserService.getSystemUser(tenantId);

    wireMockServer.verify(postRequestedFor(urlEqualTo("/authn/login")));
    assertThat(systemUser.getTenantId()).isEqualTo(tenantId);
    assertThat(systemUser.getToken()).isEqualTo("aa.bb.cc");
    assertThat(systemUser.getUsername()).isEqualTo("mod-search");

    // verify that the token is cached
    var systemUserCache = cacheManager.getCache(SYSTEM_USER_CACHE);
    assertThat(systemUserCache).isNotNull();
    assertThat(systemUserCache.get(tenantId, SystemUser.class)).isEqualTo(systemUser);

    removeTenant(tenantId);
  }
}
