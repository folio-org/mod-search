package org.folio.search.configuration.opensearch;

import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;

public interface RestClientBuilderCustomizer {

  void customize(HttpAsyncClientBuilder builder);

  void customize(Builder builder);

}
