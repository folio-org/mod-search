package org.folio.search.service.systemuser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.service.systemuser.InMemorySystemUserService.SYSTEM_USER_CACHE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import org.folio.search.model.SystemUser;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@IntegrationTest
class InMemorySystemUserServiceIT extends BaseIntegrationTest {
  @SpyBean
  private SystemUserService service;
  @Autowired
  private CacheManager cacheManager;

  @SuppressWarnings("unused")
  @DynamicPropertySource
  static void specifyOkapiUrl(DynamicPropertyRegistry registry) {
    registry.add("okapi.url", BaseIntegrationTest::getOkapiUrl);
  }

  @Test
  void shouldUseInMemoryService() {
    setUpTenant("tenant_1", mockMvc);

    assertThat(service).isInstanceOf(InMemorySystemUserService.class);
    verify(service, atLeastOnce()).prepareSystemUser(any());
  }

  @Test
  void shouldCacheToken() {
    var tenant = "tenant_2";
    setUpTenant(tenant, mockMvc);
    var systemUser = service.getSystemUser(tenant);

    assertThat(systemUser).isNotNull();
    assertThat(systemUser.getToken()).isNotNull();
    assertThat(cacheManager.getCache(SYSTEM_USER_CACHE).get(tenant, SystemUser.class))
      .isEqualTo(systemUser);
  }
}
