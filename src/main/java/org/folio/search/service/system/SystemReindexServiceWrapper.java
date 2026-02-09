package org.folio.search.service.system;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.IndexService;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.reindex.ReindexService;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class SystemReindexServiceWrapper {

  private final ReindexService reindexService;
  private final IndexService indexService;
  private final ResourceDescriptionService resourceDescriptionService;

  @Retryable(maxRetries = 2, delay = 1000, multiplier = 2)
  public void doReindex(ResourceType resource, String tenantId) {
    try {
      if (!resourceDescriptionService.get(resource).isReindexSupported()) {
        return;
      }
      if (resource.getName().equals(ReindexEntityType.INSTANCE.getType())) {
        reindexService.submitFullReindex(tenantId, null);
      } else {
        var resourceName = ReindexRequest.ResourceNameEnum.fromValue(resource.getName());
        indexService.reindexInventory(tenantId, new ReindexRequest().resourceName(resourceName));
      }
    } catch (Exception e) {
      log.warn("Reindex failed for tenant {} with exception: {}", tenantId, e.getMessage());
      throw e;
    }
  }
}
