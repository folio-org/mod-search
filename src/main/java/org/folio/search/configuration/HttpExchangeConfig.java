package org.folio.search.configuration;

import org.folio.search.client.ConsortiumTenantsClient;
import org.folio.search.client.InventoryInstanceClient;
import org.folio.search.client.InventoryReferenceDataClient;
import org.folio.search.client.InventoryReindexRecordsClient;
import org.folio.search.client.LocationsClient;
import org.folio.search.client.ResourceReindexClient;
import org.folio.search.client.UserTenantsClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpExchangeConfig {

  @Bean
  public ConsortiumTenantsClient consortiumTenantsClient(HttpServiceProxyFactory factory) {
    return factory.createClient(ConsortiumTenantsClient.class);
  }

  @Bean
  public InventoryInstanceClient inventoryInstanceClient(HttpServiceProxyFactory factory) {
    return factory.createClient(InventoryInstanceClient.class);
  }

  @Bean
  public InventoryReferenceDataClient inventoryReferenceDataClient(HttpServiceProxyFactory factory) {
    return factory.createClient(InventoryReferenceDataClient.class);
  }

  @Bean
  public InventoryReindexRecordsClient inventoryReindexRecordsClient(HttpServiceProxyFactory factory) {
    return factory.createClient(InventoryReindexRecordsClient.class);
  }

  @Bean
  public LocationsClient locationsClient(HttpServiceProxyFactory factory) {
    return factory.createClient(LocationsClient.class);
  }

  @Bean
  public ResourceReindexClient resourceReindexClient(HttpServiceProxyFactory factory) {
    return factory.createClient(ResourceReindexClient.class);
  }

  @Bean
  public UserTenantsClient userTenantsClient(HttpServiceProxyFactory factory) {
    return factory.createClient(UserTenantsClient.class);
  }
}
