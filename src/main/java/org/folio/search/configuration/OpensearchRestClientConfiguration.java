/*
 * Copyright 2012-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.folio.search.configuration;

import static org.opensearch.client.RestClientBuilder.DEFAULT_MAX_CONN_PER_ROUTE;
import static org.opensearch.client.RestClientBuilder.DEFAULT_MAX_CONN_TOTAL;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;
import org.folio.search.configuration.opensearch.RestClientBuilderCustomizer;
import org.folio.search.configuration.properties.OpensearchProperties;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class OpensearchRestClientConfiguration {

  @Bean
  RestClientBuilderCustomizer defaultRestClientBuilderCustomizer(OpensearchProperties properties) {
    return new DefaultRestClientBuilderCustomizer(properties);
  }

  @Bean
  RestClientBuilder opensearchRestClientBuilder(ObjectProvider<RestClientBuilderCustomizer> builderCustomizers,
                                                OpensearchProperties properties) {
    HttpHost[] hosts = properties.getUris().stream().map(this::createHttpHost).toArray(HttpHost[]::new);
    RestClientBuilder builder = RestClient.builder(hosts);
    builder.setHttpClientConfigCallback(httpClientBuilder -> {
      builderCustomizers.orderedStream().forEach(customizer -> customizer.customize(httpClientBuilder));
      return httpClientBuilder;
    });
    builder.setRequestConfigCallback(requestConfigBuilder -> {
      builderCustomizers.orderedStream().forEach(customizer -> customizer.customize(requestConfigBuilder));
      return requestConfigBuilder;
    });
    if (properties.getPathPrefix() != null) {
      builder.setPathPrefix(properties.getPathPrefix());
    }
    builder.setCompressionEnabled(properties.isCompressionEnabled());
    return builder;
  }

  @Bean
  RestHighLevelClient opensearchRestHighLevelClient(RestClientBuilder restClientBuilder) {
    return new RestHighLevelClient(restClientBuilder);
  }

  @Bean
  @ConditionalOnClass(RestHighLevelClient.class)
  RestClient opensearchRestClient(RestHighLevelClient restHighLevelClient) {
    return restHighLevelClient.getLowLevelClient();
  }

  private HttpHost createHttpHost(String uri) {
    try {
      return createHttpHost(URI.create(uri));
    } catch (IllegalArgumentException ex) {
      try {
        return HttpHost.create(uri);
      } catch (URISyntaxException innerEx) {
        throw new IllegalStateException(innerEx);
      }
    }
  }

  private HttpHost createHttpHost(URI uri) {
    try {
      if (!StringUtils.hasLength(uri.getUserInfo())) {
        return HttpHost.create(uri.toString());
      }
      return HttpHost.create(new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(),
        uri.getQuery(), uri.getFragment()).toString());
    } catch (URISyntaxException ex) {
      throw new IllegalStateException(ex);
    }
  }

  static class DefaultRestClientBuilderCustomizer implements RestClientBuilderCustomizer {

    private static final PropertyMapper MAPPER = PropertyMapper.get();

    private final OpensearchProperties properties;

    DefaultRestClientBuilderCustomizer(OpensearchProperties properties) {
      this.properties = properties;
    }

    @Override
    public void customize(HttpAsyncClientBuilder builder) {
      builder.setDefaultCredentialsProvider(new PropertiesCredentialsProvider(this.properties));
      builder.setConnectionManager(getPoolingAsyncClientConnectionManager());
      if (properties.isElasticsearchServer()) {
        builder.addRequestInterceptorFirst((HttpRequest request, EntityDetails entityDetails, HttpContext context) -> {
          var uri = request.getRequestUri();
          if (uri.contains("cluster_manager_timeout")) {
            var newUri = uri.replaceAll("[&?]?cluster_manager_timeout=[^&]*", "");
            request.setPath(newUri);
          }
        });
      }
    }

    @Override
    public void customize(RequestConfig.Builder builder) {
      MAPPER.from(this.properties::getSocketTimeout).whenNonNull().asInt(Duration::toMillis)
        .to(timeout -> builder.setResponseTimeout(Timeout.ofMilliseconds(timeout)));
    }

    private PoolingAsyncClientConnectionManager getPoolingAsyncClientConnectionManager() {
      return PoolingAsyncClientConnectionManagerBuilder.create()
        .setDefaultConnectionConfig(getConnectionConfig(ConnectionConfig.custom()))
        .setMaxConnPerRoute(DEFAULT_MAX_CONN_PER_ROUTE)
        .setMaxConnTotal(DEFAULT_MAX_CONN_TOTAL)
        .setTlsStrategy(getTlsStrategy())
        .build();
    }

    private ConnectionConfig getConnectionConfig(ConnectionConfig.Builder builder) {
      MAPPER.from(this.properties::getConnectionTimeout).whenNonNull().asInt(Duration::toMillis)
        .to(timeout -> builder.setConnectTimeout(Timeout.ofMilliseconds(timeout)));
      MAPPER.from(this.properties::getSocketTimeout).whenNonNull().asInt(Duration::toMillis)
        .to(timeout -> builder.setSocketTimeout(Timeout.ofMilliseconds(timeout)));
      return builder.build();
    }

    private static TlsStrategy getTlsStrategy() {
      try {
        return ClientTlsStrategyBuilder.create()
          .setSslContext(SSLContext.getDefault())
          .build();
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("Could not create the default ssl context", e);
      }
    }
  }

  private static class PropertiesCredentialsProvider extends BasicCredentialsProvider {

    PropertiesCredentialsProvider(OpensearchProperties properties) {
      if (StringUtils.hasText(properties.getUsername())) {
        var credentials = new UsernamePasswordCredentials(properties.getUsername(),
          properties.getPassword().toCharArray());
        setCredentials(new AuthScope(null, -1), credentials);
      }
      properties.getUris().stream().map(this::toUri).filter(this::hasUserInfo).forEach(this::addUserInfoCredentials);
    }

    private URI toUri(String uri) {
      try {
        return URI.create(uri);
      } catch (IllegalArgumentException ex) {
        return null;
      }
    }

    private boolean hasUserInfo(URI uri) {
      return uri != null && StringUtils.hasLength(uri.getUserInfo());
    }

    private void addUserInfoCredentials(URI uri) {
      AuthScope authScope = new AuthScope(uri.getHost(), uri.getPort());
      Credentials credentials = createUserInfoCredentials(uri.getUserInfo());
      setCredentials(authScope, credentials);
    }

    private Credentials createUserInfoCredentials(String userInfo) {
      int delimiter = userInfo.indexOf(":");
      if (delimiter == -1) {
        return new UsernamePasswordCredentials(userInfo, null);
      }
      String username = userInfo.substring(0, delimiter);
      String password = userInfo.substring(delimiter + 1);
      return new UsernamePasswordCredentials(username, password.toCharArray());
    }

  }

}
