package org.folio.search.configuration;

import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.service.consortia.ConsortiaService;
import org.folio.search.service.consortia.ConsortiaTenantProvider;
import org.folio.search.service.consortia.TenantProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConsortiaConfig {

  @Bean
  public TenantProvider getTenantProvider(SearchConfigurationProperties searchConfigurationProperties,
                                          ConsortiaService consortiaService) {

    if (searchConfigurationProperties.inConsortiaMode()) {
      return new ConsortiaTenantProvider(consortiaService);
    } else {
      return tenantId -> tenantId;
    }
  }
}
