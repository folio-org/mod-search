package org.folio.search.configuration;

import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.service.consortium.TenantProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConsortiaConfig {

  @Bean
  public TenantProvider getTenantProvider(SearchConfigurationProperties searchConfigurationProperties,
                                          ConsortiumTenantService consortiumTenantService) {

    if (searchConfigurationProperties.inConsortiaMode()) {
      return new ConsortiumTenantProvider(consortiumTenantService);
    } else {
      return tenantId -> tenantId;
    }
  }
}
