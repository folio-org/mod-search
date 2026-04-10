package org.folio.search.controller;

import static org.folio.search.model.service.CqlResourceIdsRequest.HOLDINGS_ID_PATH;
import static org.folio.search.model.service.CqlResourceIdsRequest.INSTANCE_ID_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.model.types.ResourceType;
import org.folio.search.rest.resource.SearchResourcesIdsApi;
import org.folio.search.service.ResourceIdsJobService;
import org.folio.search.service.ResourceIdsStreamHelper;
import org.folio.search.service.VersionedResourceIdService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class ResourcesIdsController implements SearchResourcesIdsApi {

  private final ResourceIdsStreamHelper resourceIdsStreamHelper;
  private final ResourceIdsJobService resourceIdsJobService;
  private final VersionedResourceIdService versionedResourceIdService;
  private final QueryVersionRequestHelper queryVersionRequestHelper;

  @Override
  public ResponseEntity<Void> getHoldingIds(String query, String tenantId, String contentType) {
    var bulkRequest = CqlResourceIdsRequest.of(ResourceType.INSTANCE, tenantId, query, HOLDINGS_ID_PATH);
    return streamVersionedResourceIds(bulkRequest, contentType, queryVersionRequestHelper.resolve());
  }

  @Override
  public ResponseEntity<ResourceIdsJob> getIdsJob(String tenantId, String jobId) {
    return ResponseEntity.ok(resourceIdsJobService.getJobById(jobId));
  }

  @Override
  public ResponseEntity<Void> getInstanceIds(String query, String tenantId, String contentType) {
    var request = CqlResourceIdsRequest.of(ResourceType.INSTANCE, tenantId, query, INSTANCE_ID_PATH);
    return streamVersionedResourceIds(request, contentType, queryVersionRequestHelper.resolve());
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

  private ResponseEntity<Void> streamVersionedResourceIds(CqlResourceIdsRequest request,
                                                           String contentType, String queryVersion) {
    try {
      var requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (requestAttributes == null || requestAttributes.getResponse() == null) {
        throw new SearchServiceException("HttpServletResponse must be not null");
      }
      var response = requestAttributes.getResponse();
      response.setStatus(200);

      if (contentType != null && contentType.contains(TEXT_PLAIN_VALUE)) {
        response.setContentType(TEXT_PLAIN_VALUE);
        versionedResourceIdService.streamResourceIdsAsText(request, response.getOutputStream(), queryVersion);
      } else {
        response.setContentType(APPLICATION_JSON_VALUE);
        versionedResourceIdService.streamResourceIdsAsJson(request, response.getOutputStream(), queryVersion);
      }
      return ResponseEntity.ok().build();
    } catch (IOException e) {
      throw new SearchServiceException("Failed to get output stream from response", e);
    }
  }
}
