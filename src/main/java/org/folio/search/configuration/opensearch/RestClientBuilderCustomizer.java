package org.folio.search.configuration.opensearch;

import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;

public interface RestClientBuilderCustomizer {

  void customize(HttpAsyncClientBuilder builder);

  void customize(Builder builder);

}
