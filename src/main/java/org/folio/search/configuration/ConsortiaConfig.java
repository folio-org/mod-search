package org.folio.search.configuration;

import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.service.consortium.ConsortiaTenantProvider;
import org.folio.search.service.consortium.ConsortiaTenantService;
import org.folio.search.service.consortium.TenantProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConsortiaConfig {

  @Bean
  public TenantProvider getTenantProvider(SearchConfigurationProperties searchConfigurationProperties,
                                          ConsortiaTenantService consortiaTenantService) {

    if (searchConfigurationProperties.inConsortiaMode()) {
      return new ConsortiaTenantProvider(consortiaTenantService);
    } else {
      return tenantId -> tenantId;
    }
  }
}
