package org.folio.search.service;

import static java.util.Collections.singleton;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.scope.FolioExecutionContextService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
public class EgressExecutionContextService extends FolioExecutionContextService {

  private final String folioUrl;

  /**
   * Constructs a FolioContextExecutionService with the given module metadata.
   *
   * @param moduleMetadata the module metadata to use for context setup
   */
  public EgressExecutionContextService(FolioModuleMetadata moduleMetadata,
                                       @Value("${folio.okapi-url}") String folioUrl) {
    super(moduleMetadata);
    this.folioUrl = folioUrl;
  }

  public <T> T execute(String tenantId, Callable<T> action) {
    Map<String, Collection<String>> allHeaders = new HashMap<>();
    allHeaders.put(XOkapiHeaders.URL, singleton(folioUrl));
    return execute(tenantId, allHeaders, action);
  }
}
