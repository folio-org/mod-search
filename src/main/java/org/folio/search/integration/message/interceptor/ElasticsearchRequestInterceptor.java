package org.folio.search.integration.message.interceptor;

import java.util.Set;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.opensearch.elasticsearch-server", havingValue = "true")
public class ElasticsearchRequestInterceptor implements HttpRequestInterceptor {

  private static final String CLUSTER_MANAGER_TIMEOUT_PARAM = "cluster_manager_timeout";
  private static final String MASTER_TIMEOUT_PARAM = "master_timeout";
  private static final Set<String> SUPPORTED_METHODS = Set.of("POST", "PUT", "DELETE");

  @Override
  public void process(HttpRequest request, EntityDetails entityDetails, HttpContext context) {
    if (SUPPORTED_METHODS.contains(request.getMethod().toUpperCase())) {
      var uri = request.getRequestUri();
      if (uri.contains(CLUSTER_MANAGER_TIMEOUT_PARAM)) {
        request.setPath(uri.replace(CLUSTER_MANAGER_TIMEOUT_PARAM, MASTER_TIMEOUT_PARAM));
      }
    }
  }
}
