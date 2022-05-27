package org.folio.search;

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
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class OpensearchConfig {

  @Bean
  ElasticsearchProperties elasticsearchProperties() {
    return new ElasticsearchProperties();
  }

  private static class PropertiesCredentialsProvider extends BasicCredentialsProvider {
    PropertiesCredentialsProvider(ElasticsearchProperties properties) {
      if (StringUtils.hasText(properties.getUsername())) {
        Credentials credentials = new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword());
        this.setCredentials(AuthScope.ANY, credentials);
      }

      properties.getUris().stream().map(this::toUri).filter(this::hasUserInfo).forEach(this::addUserInfoCredentials);
    }

    private URI toUri(String uri) {
      try {
        return URI.create(uri);
      } catch (IllegalArgumentException var3) {
        return null;
      }
    }

    private boolean hasUserInfo(URI uri) {
      return uri != null && StringUtils.hasLength(uri.getUserInfo());
    }

    private void addUserInfoCredentials(URI uri) {
      AuthScope authScope = new AuthScope(uri.getHost(), uri.getPort());
      Credentials credentials = this.createUserInfoCredentials(uri.getUserInfo());
      this.setCredentials(authScope, credentials);
    }

    private Credentials createUserInfoCredentials(String userInfo) {
      int delimiter = userInfo.indexOf(":");
      if (delimiter == -1) {
        return new UsernamePasswordCredentials(userInfo, (String)null);
      } else {
        String username = userInfo.substring(0, delimiter);
        String password = userInfo.substring(delimiter + 1);
        return new UsernamePasswordCredentials(username, password);
      }
    }
  }

  @Bean
  RestHighLevelClient elasticsearchRestHighLevelClient(RestClientBuilder restClientBuilder) {
    return new RestHighLevelClient(restClientBuilder);
  }

  @Bean
  DefaultRestClientBuilderCustomizer defaultRestClientBuilderCustomizer(ElasticsearchProperties properties) {
    return new DefaultRestClientBuilderCustomizer(properties);
  }

  @Bean
  RestClientBuilder elasticsearchRestClientBuilder(ObjectProvider<DefaultRestClientBuilderCustomizer> builderCustomizers,
                                                   ElasticsearchProperties properties) {
    HttpHost[] hosts = (HttpHost[])properties.getUris().stream().map(this::createHttpHost).toArray((x$0) -> {
      return new HttpHost[x$0];
    });
    RestClientBuilder builder = RestClient.builder(hosts);
    builder.setHttpClientConfigCallback((httpClientBuilder) -> {
      builderCustomizers.orderedStream().forEach((customizer) -> {
        customizer.customize(httpClientBuilder);
      });
      return httpClientBuilder;
    });
    builder.setRequestConfigCallback((requestConfigBuilder) -> {
      builderCustomizers.orderedStream().forEach((customizer) -> {
        customizer.customize(requestConfigBuilder);
      });
      return requestConfigBuilder;
    });
    if (properties.getPathPrefix() != null) {
      builder.setPathPrefix(properties.getPathPrefix());
    }

    builderCustomizers.orderedStream().forEach((customizer) -> {
      customizer.customize(builder);
    });
    return builder;
  }

  private HttpHost createHttpHost(String uri) {
    try {
      return this.createHttpHost(URI.create(uri));
    } catch (IllegalArgumentException var3) {
      return HttpHost.create(uri);
    }
  }

  private HttpHost createHttpHost(URI uri) {
    if (!StringUtils.hasLength(uri.getUserInfo())) {
      return HttpHost.create(uri.toString());
    } else {
      try {
        return HttpHost.create((new URI(uri.getScheme(), (String)null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment())).toString());
      } catch (URISyntaxException var3) {
        throw new IllegalStateException(var3);
      }
    }
  }

  static class DefaultRestClientBuilderCustomizer {
    private static final PropertyMapper map = PropertyMapper.get();
    private final ElasticsearchProperties properties;

    DefaultRestClientBuilderCustomizer(ElasticsearchProperties properties) {
      this.properties = properties;
    }

    public void customize(RestClientBuilder builder) {
    }

    public void customize(HttpAsyncClientBuilder builder) {
      builder.setDefaultCredentialsProvider(new PropertiesCredentialsProvider(this.properties));
    }

    public void customize(RequestConfig.Builder builder) {
      map.from(this.properties::getConnectionTimeout).whenNonNull().asInt(Duration::toMillis)
        .to(builder::setConnectTimeout);
      map.from(this.properties::getSocketTimeout).whenNonNull().asInt(Duration::toMillis)
        .to(builder::setSocketTimeout);
    }
  }
}
