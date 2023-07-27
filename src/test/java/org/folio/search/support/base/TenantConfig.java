package org.folio.search.support.base;

import static org.folio.search.utils.TestConstants.CONSORTIUM_TENANT_ID;

import org.folio.search.service.consortium.TenantProvider;
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
  // todo(MSEARCH-562): maybe return property for consortium enabled/disabled just for tests (see file change history)
  @Bean
  public String centralTenant() {
    return CONSORTIUM_TENANT_ID;
  }

  @Bean
  public TenantProvider tenantProvider(String centralTenant) {
    return tenantId -> centralTenant;
  }
}
