package org.folio.search.repository;

import static java.util.Locale.ROOT;
import static org.folio.spring.config.properties.FolioEnvironment.getFolioEnvName;

import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.consortium.TenantProvider;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class IndexNameProvider {

  private final TenantProvider tenantProvider;

  public IndexNameProvider(TenantProvider tenantProvider) {
    this.tenantProvider = tenantProvider;
  }

  public String getIndexName(ResourceType resource, String tenantId) {
    return getIndexName(resource.getName(), tenantId);
  }

  public String getIndexName(SearchDocumentBody doc) {
    return getIndexName(doc.getResource(), doc.getTenant());
  }

  public String getIndexName(ResourceRequest request) {
    return getIndexName(request.getResource(), request.getTenantId());
  }

  public String getIndexName(ResourceEvent event) {
    return getIndexName(event.getResourceName(), event.getTenant());
  }

  private String getIndexName(String resource, String tenantId) {
    var finalTenantId = tenantProvider.getTenant(tenantId);
    log.debug("Calculating index name for tenant [resource: {}, original: {}, final: {}]",
      resource, tenantId, finalTenantId);
    return getFolioEnvName().toLowerCase(ROOT) + "_" + resource + "_" + finalTenantId;
  }
}
