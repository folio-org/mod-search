package org.folio.search.controller;

import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.rest.resource.InstancesApi;
import org.folio.search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller with set of endpoints for manipulating with Elasticsearch search API.
 */
@Validated
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController implements InstancesApi {

  private final SearchService searchService;

  @Override
  public ResponseEntity<SearchResult> searchInstances(String query, String tenantId, Boolean expandAll,
    Integer limit, Integer offset) {

    var searchResult = searchService.search(CqlSearchRequest.builder()
      .cqlQuery(query)
      .resource(INSTANCE_RESOURCE)
      .tenantId(tenantId)
      .limit(limit)
      .offset(offset)
      .expandAll(expandAll)
      .build());

    return ResponseEntity.ok(searchResult);
  }
}
