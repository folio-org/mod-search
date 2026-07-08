package org.folio.search.repository;

import static java.util.Locale.ROOT;
import static org.folio.spring.tools.config.properties.FolioEnvironment.getFolioEnvName;

import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.consortium.TenantProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class IndexNameProvider {

  private final TenantProvider tenantProvider;
  private final String indexSuffix;

  public IndexNameProvider(TenantProvider tenantProvider,
                           @Value("${folio.index.suffix:}") String indexSuffix) {
    this.tenantProvider = tenantProvider;
    this.indexSuffix = indexSuffix;
  }

  public String getIndexName(ResourceType resource, String tenantId) {
    return getIndexName(resource.getName(), tenantId);
  }

  public String getIndexName(SearchDocumentBody doc) {
    return getIndexName(doc.getResource(), doc.getTenant());
  }

  public String getIndexName(ResourceRequest request) {
    return getIndexName(request.resource(), request.tenantId());
  }

  public String getIndexName(ResourceEvent event) {
    return getIndexName(event.getResourceName(), event.getTenant());
  }

  private String getIndexName(String resource, String tenantId) {
    var finalTenantId = tenantProvider.getTenant(tenantId);
    log.debug("Calculating index name for tenant [resource: {}, original: {}, final: {}, suffix: {}]",
      resource, tenantId, finalTenantId, indexSuffix);
    return getFolioEnvName().toLowerCase(ROOT) + "_" + resource + "_" + finalTenantId + indexSuffix;
  }
}
