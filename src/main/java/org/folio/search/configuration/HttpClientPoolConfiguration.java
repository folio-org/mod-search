package org.folio.search.configuration;

import lombok.extern.log4j.Log4j2;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.folio.search.configuration.properties.HttpClientPoolProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.NotFoundRestClientAdapterDecorator;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Overrides the default {@link HttpServiceProxyFactory} provided by folio-spring-base so that all
 * {@code @HttpExchange} clients (including the {@code InventoryReindexRecordsClient} used during the
 * reindex merge phase) share a properly sized Apache HttpClient connection pool.
 *
 * <p>folio-spring-base builds its {@code RestClient} without an explicit request factory, which falls
 * back to the un-tuned default pool (a few connections per route). During a full reindex many publisher
 * threads call mod-inventory-storage concurrently and end up serialized on that tiny pool. This
 * configuration reuses the folio-spring-base {@link RestClient.Builder} (so the Folio URL/header
 * enrichment interceptor and message converters are preserved) and only swaps in a pooled request
 * factory.</p>
 *
 * <p>No socket/response (read) timeout is configured: reindex calls to mod-inventory-storage are
 * long-running and must not be interrupted. Only the connection pool size and connect timeout are
 * tuned. See {@link HttpClientPoolProperties}.</p>
 */
@Log4j2
@Configuration
public class HttpClientPoolConfiguration {

  @Bean
  @Primary
  public HttpServiceProxyFactory pooledHttpServiceProxyFactory(RestClient.Builder restClientBuilder,
                                                               HttpClientPoolProperties properties) {
    log.info("Configuring shared HttpExchange client connection pool [maxConnPerRoute={}, maxConnTotal={}, "
             + "connectTimeout={}]", properties.getMaxConnectionsPerRoute(), properties.getMaxConnectionsTotal(),
      properties.getConnectTimeout());

    var restClient = restClientBuilder.clone()
      .requestFactory(pooledRequestFactory(properties))
      .build();

    return HttpServiceProxyFactory
      .builderFor(RestClientAdapter.create(restClient))
      .exchangeAdapterDecorator(NotFoundRestClientAdapterDecorator::new)
      .build();
  }

  private ClientHttpRequestFactory pooledRequestFactory(HttpClientPoolProperties properties) {
    var connectionConfigBuilder = ConnectionConfig.custom()
      .setConnectTimeout(Timeout.of(properties.getConnectTimeout()));
    if (properties.getConnectionTimeToLive() != null) {
      connectionConfigBuilder.setTimeToLive(TimeValue.of(properties.getConnectionTimeToLive()));
    }
    if (properties.getValidateAfterInactivity() != null) {
      connectionConfigBuilder.setValidateAfterInactivity(TimeValue.of(properties.getValidateAfterInactivity()));
    }

    var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
      .setMaxConnPerRoute(properties.getMaxConnectionsPerRoute())
      .setMaxConnTotal(properties.getMaxConnectionsTotal())
      .setDefaultConnectionConfig(connectionConfigBuilder.build())
      .build();

    // Intentionally no response/socket timeout: reindex publish calls to mod-inventory-storage
    // are long-running and must not be interrupted by a read timeout.
    var httpClient = HttpClients.custom()
      .setConnectionManager(connectionManager)
      .build();

    return new HttpComponentsClientHttpRequestFactory(httpClient);
  }
}

