package org.folio.search.support.base;

import static org.folio.search.utils.TestConstants.CONSORTIUM_TENANT_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;

import org.folio.search.service.consortia.TenantProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Needed to run tests in both modes (consortia on/off).
 * Provides central tenant (regular tenant if consortia off).
 * Provides TenantProvider that returns central tenant
 * */
@Configuration
public class TenantConfig {

  @Bean
  public Boolean inConsortiumMode(
    @Value("${folio.search-config.search-features.consortium}") Boolean inConsortiumMode) {
    return inConsortiumMode;
  }

  @Bean
  public String centralTenant(Boolean inConsortiumMode) {
    if (inConsortiumMode) {
      return CONSORTIUM_TENANT_ID;
    }
    return TENANT_ID;
  }

  @Bean
  public TenantProvider tenantProvider(String centralTenant) {
    return tenantId -> centralTenant;
  }
}
