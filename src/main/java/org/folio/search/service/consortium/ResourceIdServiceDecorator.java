package org.folio.search.service.consortium;

import java.io.OutputStream;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.model.streamids.ResourceIdsJobEntity;
import org.folio.search.service.ResourceIdService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ResourceIdServiceDecorator {

  private final ConsortiaTenantExecutor consortiaTenantExecutor;
  private final ResourceIdService resourceIdService;

  public void streamResourceIdsAsText(CqlResourceIdsRequest request, OutputStream outputStream) {
    resourceIdService.streamResourceIdsAsText(request, outputStream);
  }

  public void streamIdsFromDatabaseAsJson(String jobId, OutputStream outputStream) {
    consortiaTenantExecutor.run(() -> resourceIdService.streamIdsFromDatabaseAsJson(jobId, outputStream));
  }

  public void streamResourceIdsForJob(ResourceIdsJobEntity job, String tenantId) {
    consortiaTenantExecutor.run(() -> resourceIdService.streamResourceIdsForJob(job, tenantId));
  }

  public void streamResourceIdsAsJson(CqlResourceIdsRequest request, OutputStream outputStream) {
    resourceIdService.streamResourceIdsAsJson(request, outputStream);
  }

}
