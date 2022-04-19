package org.folio.search.controller;

import static org.folio.search.model.service.CqlResourceIdsRequest.AUTHORITY_ID_PATH;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthoritySearchResult;
import org.folio.search.domain.dto.ResourceIdsJob;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.rest.resource.AuthoritiesApi;
import org.folio.search.service.ResourceIdsJobService;
import org.folio.search.service.ResourceIdsStreamHelper;
import org.folio.search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/search")
public class AuthorityController implements AuthoritiesApi {

  private final SearchService searchService;
  private final ResourceIdsStreamHelper resourceIdsStreamHelper;
  private final ResourceIdsJobService resourceIdsJobService;

  @Override
  public ResponseEntity<ResourceIdsJob> getAuthoritiesIdsJob(String tenantId, String jobId) {
    return ResponseEntity.ok(resourceIdsJobService.getJobById(jobId));
  }

  @Override
  public ResponseEntity<ResourceIdsJob> submitAuthoritiesIdsJob(String tenantId, ResourceIdsJob resourceIdsJob) {
    resourceIdsJob.setEntityType(ResourceIdsJob.EntityTypeEnum.AUTHORITY);
    return ResponseEntity.ok(resourceIdsJobService.createStreamJob(resourceIdsJob));
  }

  @Override
  public ResponseEntity<Void> getAuthorityIds(String query, String tenantId) {
    var request = CqlResourceIdsRequest.of(AUTHORITY_RESOURCE, tenantId, query, AUTHORITY_ID_PATH);
    return resourceIdsStreamHelper.streamResourceIdsFromDb(request);
  }

  @Override
  public ResponseEntity<AuthoritySearchResult> searchAuthorities(
    String tenant, String query, Integer limit, Integer offset, Boolean expandAll) {
    var searchRequest = CqlSearchRequest.of(Authority.class, tenant, query, limit, offset, expandAll);
    var result = searchService.search(searchRequest);
    return ResponseEntity.ok(new AuthoritySearchResult()
      .authorities(result.getRecords())
      .totalRecords(result.getTotalRecords()));
  }
}
