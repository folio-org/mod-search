package org.folio.search.service.consortium;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.service.ResourceIdsJobService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ResourceIdsJobServiceDecorator {

  private final ConsortiumTenantExecutor consortiumTenantExecutor;
  private final ResourceIdsJobService resourceIdsJobService;

  public ResourceIdsJob getJobById(String id) {
    return consortiumTenantExecutor.execute(() -> resourceIdsJobService.getJobById(id));
  }

  public ResourceIdsJob createStreamJob(ResourceIdsJob job, String tenantId) {
    return consortiumTenantExecutor.execute(() -> resourceIdsJobService.createStreamJob(job, tenantId));
  }

}
