package org.folio.search.configuration.opensearch;

import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.opensearch.client.RestClientBuilder;

@FunctionalInterface
public interface RestClientBuilderCustomizer {

  void customize(RestClientBuilder builder);

  default void customize(HttpAsyncClientBuilder builder) {
  }

  default void customize(Builder builder) {
  }

}
