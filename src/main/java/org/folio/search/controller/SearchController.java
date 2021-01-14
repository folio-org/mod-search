package org.folio.search.controller;

import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.TENANT_HEADER;

import lombok.RequiredArgsConstructor;
import org.folio.search.model.rest.request.SearchRequestBody;
import org.folio.search.model.rest.response.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.service.SearchService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller with set of endpoints for manipulating with Elasticsearch search API.
 */
@Validated
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

  private final SearchService searchService;

  /**
   * Performs search request for passed {@link SearchRequestBody} object.
   *
   * @param requestBody search request body
   * @param tenantId tenant id from request header
   * @return search result as {@link SearchResult} object
   */
  @GetMapping("/instances")
  public SearchResult searchInstances(
    SearchRequestBody requestBody,
    @RequestHeader(TENANT_HEADER) String tenantId) {
    return searchService.search(CqlSearchRequest.builder()
      .cqlQuery(requestBody.getQuery())
      .resource(INSTANCE_RESOURCE)
      .limit(requestBody.getLimit())
      .offset(requestBody.getOffset())
      .tenantId(tenantId)
      .build());
  }
}
