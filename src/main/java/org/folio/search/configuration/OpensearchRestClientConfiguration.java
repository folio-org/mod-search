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

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.folio.search.configuration.opensearch.RestClientBuilderCustomizer;
import org.folio.search.configuration.properties.OpensearchProperties;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
  @ConditionalOnMissingBean(RestClientBuilder.class)
  RestClientBuilder elasticsearchRestClientBuilder(ObjectProvider<RestClientBuilderCustomizer> builderCustomizers,
                                                   OpensearchProperties properties) {
    HttpHost[] hosts = properties.getUris().stream().map(this::createHttpHost).toArray(HttpHost[]::new);
    RestClientBuilder builder = RestClient.builder(hosts);
    builder.setHttpClientConfigCallback((httpClientBuilder) -> {
      builderCustomizers.orderedStream().forEach((customizer) -> customizer.customize(httpClientBuilder));
      return httpClientBuilder;
    });
    builder.setRequestConfigCallback((requestConfigBuilder) -> {
      builderCustomizers.orderedStream().forEach((customizer) -> customizer.customize(requestConfigBuilder));
      return requestConfigBuilder;
    });
    if (properties.getPathPrefix() != null) {
      builder.setPathPrefix(properties.getPathPrefix());
    }
    builderCustomizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
    return builder;
  }

  @Bean
  RestHighLevelClient elasticsearchRestHighLevelClient(RestClientBuilder restClientBuilder) {
    return new RestHighLevelClient(restClientBuilder);
  }

  @Bean
  @ConditionalOnClass(RestHighLevelClient.class)
  RestClient elasticsearchRestClient(RestHighLevelClient restHighLevelClient) {
    return restHighLevelClient.getLowLevelClient();
  }

  private HttpHost createHttpHost(String uri) {
    try {
      return createHttpHost(URI.create(uri));
    } catch (IllegalArgumentException ex) {
      return HttpHost.create(uri);
    }
  }

  private HttpHost createHttpHost(URI uri) {
    if (!StringUtils.hasLength(uri.getUserInfo())) {
      return HttpHost.create(uri.toString());
    }
    try {
      return HttpHost.create(new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(),
        uri.getQuery(), uri.getFragment()).toString());
    } catch (URISyntaxException ex) {
      throw new IllegalStateException(ex);
    }
  }

  static class DefaultRestClientBuilderCustomizer implements RestClientBuilderCustomizer {

    private static final PropertyMapper map = PropertyMapper.get();

    private final OpensearchProperties properties;

    DefaultRestClientBuilderCustomizer(OpensearchProperties properties) {
      this.properties = properties;
    }

    @Override
    public void customize(RestClientBuilder builder) {
    }

    @Override
    public void customize(HttpAsyncClientBuilder builder) {
      builder.setDefaultCredentialsProvider(new PropertiesCredentialsProvider(this.properties));
    }

    @Override
    public void customize(RequestConfig.Builder builder) {
      map.from(this.properties::getConnectionTimeout).whenNonNull().asInt(Duration::toMillis)
        .to(builder::setConnectTimeout);
      map.from(this.properties::getSocketTimeout).whenNonNull().asInt(Duration::toMillis)
        .to(builder::setSocketTimeout);
    }

  }

  private static class PropertiesCredentialsProvider extends BasicCredentialsProvider {

    PropertiesCredentialsProvider(OpensearchProperties properties) {
      if (StringUtils.hasText(properties.getUsername())) {
        var credentials = new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword());
        setCredentials(AuthScope.ANY, credentials);
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
      return new UsernamePasswordCredentials(username, password);
    }

  }

}
