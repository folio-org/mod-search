package org.folio.search.service.systemuser;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.configuration.SearchCacheNames.SYSTEM_USER_CACHE;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.search.domain.dto.Instance;
import org.folio.search.model.SystemUser;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.folio.tenant.domain.dto.TenantAttributes;
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
  void shouldCreateSystemUserDuringTenantInit() throws Exception {
    var tenantId = "should_create_system_user";

    mockMvc.perform(post("/_/tenant")
        .headers(defaultHeaders(tenantId))
        .content(asJsonString(new TenantAttributes().moduleTo("mod-search-1.0.0"))))
      .andExpect(status().isOk());

    getWireMock().verify(getRequestedFor(urlEqualTo("/users?query=username%3D%3Dmod-search")));
    getWireMock().verify(postRequestedFor(urlEqualTo("/users")));
    getWireMock().verify(postRequestedFor(urlEqualTo("/authn/credentials")));
    getWireMock().verify(postRequestedFor(urlEqualTo("/perms/users")));
    getWireMock().verify(postRequestedFor(urlEqualTo("/authn/login")));

    var systemUser = systemUserService.getSystemUser(tenantId);
    assertThat(systemUser.getTenantId()).isEqualTo(tenantId);
    assertThat(systemUser.getToken()).isEqualTo("aa.bb.cc");
    assertThat(systemUser.getUsername()).isEqualTo("mod-search");
  }

  @Test
  void shouldCacheToken() {
    var tenant = "tenant_2";
    setUpTenant(tenant, mockMvc, new Instance[] {});
    var systemUser = systemUserService.getSystemUser(tenant);

    assertThat(systemUser).isNotNull();
    assertThat(systemUser.getToken()).isNotNull();
    var systemUserCache = cacheManager.getCache(SYSTEM_USER_CACHE);
    assertThat(systemUserCache).isNotNull();
    assertThat(systemUserCache.get(tenant, SystemUser.class)).isEqualTo(systemUser);
  }
}
