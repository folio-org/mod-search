package org.folio.search.controller;

import lombok.RequiredArgsConstructor;
import org.folio.search.model.rest.request.SearchRequestBody;
import org.folio.search.model.rest.response.SearchResult;
import org.folio.search.service.SearchService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller with set of endpoints for manipulating with Elasticsearch search API.
 */
@Validated
@RestController
@RequestMapping("/search/")
@RequiredArgsConstructor
public class SearchController {

  private final SearchService searchService;

  /**
   * Performs search request for passed {@link SearchRequestBody} object
   *
   * @param requestBody search request body
   * @param tenantId tenant id from request header
   * @return search result as {@link SearchResult} object
   */
  @SuppressWarnings("unused")
  @PostMapping("/query")
  public SearchResult search(
    @RequestBody SearchRequestBody requestBody,
    @RequestHeader("tenant-id") String tenantId) {
    return searchService.search(requestBody.getQuery(), tenantId);
  }
}
