package org.folio.search.controller;

import static org.folio.search.model.service.CqlResourceIdsRequest.HOLDINGS_ID_PATH;
import static org.folio.search.model.service.CqlResourceIdsRequest.INSTANCE_ID_PATH;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.rest.resource.SearchResourcesIdsApi;
import org.folio.search.service.ResourceIdsJobService;
import org.folio.search.service.ResourceIdsStreamHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class ResourcesIdsController implements SearchResourcesIdsApi {

  private final ResourceIdsStreamHelper resourceIdsStreamHelper;
  private final ResourceIdsJobService resourceIdsJobService;

  @Override
  public ResponseEntity<Void> getHoldingIds(String query, String tenantId, String contentType) {
    var bulkRequest = CqlResourceIdsRequest.of(INSTANCE_RESOURCE, tenantId, query, HOLDINGS_ID_PATH);
    return resourceIdsStreamHelper.streamResourceIds(bulkRequest, contentType);
  }

  @Override
  public ResponseEntity<ResourceIdsJob> getIdsJob(String tenantId, String jobId) {
    return ResponseEntity.ok(resourceIdsJobService.getJobById(jobId));
  }

  @Override
  public ResponseEntity<Void> getInstanceIds(String query, String tenantId, String contentType) {
    var request = CqlResourceIdsRequest.of(INSTANCE_RESOURCE, tenantId, query, INSTANCE_ID_PATH);
    return resourceIdsStreamHelper.streamResourceIds(request, contentType);
  }

  @Override
  public ResponseEntity<Void> getResourceIds(String tenantId, String jobId) {
    return resourceIdsStreamHelper.streamResourceIdsFromDb(jobId);
  }

  @Override
  public ResponseEntity<ResourceIdsJob> submitIdsJob(String tenantId, ResourceIdsJob resourceIdsJob) {
    resourceIdsJob.setEntityType(ResourceIdsJob.EntityTypeEnum.valueOf(resourceIdsJob.getEntityType().name()));
    return ResponseEntity.ok(resourceIdsJobService.createStreamJob(resourceIdsJob, tenantId));
  }
}
