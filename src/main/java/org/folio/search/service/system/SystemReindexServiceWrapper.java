package org.folio.search.service.system;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.IndexService;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.reindex.ReindexService;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class SystemReindexServiceWrapper {

  private final ReindexService reindexService;
  private final IndexService indexService;
  private final ResourceDescriptionService resourceDescriptionService;

  @Retryable(maxAttempts = 2, backoff = @Backoff(delay = 1000, multiplier = 2), recover = "failureReindex")
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

  /**
   * Method to handle reindex failure after retries are exhausted.
   */
  @Recover
  private void failureReindex(Exception e, ResourceType resource, String tenantId) {
    log.error("{} reindex failed for tenant {} with exception: {}", resource, tenantId, e.getMessage());
  }
}
