package org.folio.search.controller;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.rest.resource.ResourcesApi;
import org.folio.search.service.ResourceIdsJobService;
import org.folio.search.service.ResourceIdsStreamHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/search")
public class ResourcesIdsController implements ResourcesApi {

  private final ResourceIdsStreamHelper resourceIdsStreamHelper;
  private final ResourceIdsJobService resourceIdsJobService;

  @Override
  public ResponseEntity<ResourceIdsJob> getIdsJob(String tenantId, String jobId) {
    return ResponseEntity.ok(resourceIdsJobService.getJobById(jobId));
  }

  @Override
  public ResponseEntity<ResourceIdsJob> submitIdsJob(String tenantId, ResourceIdsJob resourceIdsJob) {
    resourceIdsJob.setEntityType(ResourceIdsJob.EntityTypeEnum.valueOf(resourceIdsJob.getEntityType().name()));
    return ResponseEntity.ok(resourceIdsJobService.createStreamJob(resourceIdsJob, tenantId));
  }

  @Override
  public ResponseEntity<Void> getResourceIds(String tenantId, String jobId) {
    return resourceIdsStreamHelper.streamResourceIdsFromDb(jobId);
  }
}
