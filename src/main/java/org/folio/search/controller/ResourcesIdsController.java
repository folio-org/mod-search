package org.folio.search.controller;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.rest.resource.SearchResourcesIdsApi;
import org.folio.search.service.id.ResourceIdsJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class ResourcesIdsController implements SearchResourcesIdsApi {

  private final ResourceIdsJobService resourceIdsJobService;

  @Override
  public ResponseEntity<ResourceIdsJob> getIdsJob(String tenantId, String jobId) {
    return ResponseEntity.ok(resourceIdsJobService.getJobById(jobId));
  }

  @Override
  public ResponseEntity<Void> getResourceIds(String tenantId, String jobId) {
    return resourceIdsJobService.streamResourceIdsFromDb(jobId);
  }

  @Override
  public ResponseEntity<ResourceIdsJob> submitIdsJob(String tenantId, ResourceIdsJob resourceIdsJob) {
    return ResponseEntity.ok(resourceIdsJobService.createStreamJob(resourceIdsJob, tenantId));
  }
}
