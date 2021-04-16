package org.folio.search.controller;

import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.CqlFacetRequest;
import org.folio.search.domain.dto.CqlSearchRequest;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.SearchResult;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.mapper.SearchRequestMapper;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.rest.resource.InstancesApi;
import org.folio.search.service.FacetService;
import org.folio.search.service.ResourceIdService;
import org.folio.search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * REST controller with set of endpoints for manipulating with Elasticsearch search API.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/search")
public class SearchController implements InstancesApi {

  private final FacetService facetService;
  private final SearchService searchService;
  private final ResourceIdService resourceIdService;
  private final SearchRequestMapper searchRequestMapper;

  @Override
  public ResponseEntity<SearchResult> searchInstances(CqlSearchRequest request, String tenantId) {
    var searchRequest = searchRequestMapper.convert(request, INSTANCE_RESOURCE, tenantId);
    return ResponseEntity.ok(searchService.search(searchRequest));
  }

  @Override
  public ResponseEntity<FacetResult> getFacets(CqlFacetRequest request, String tenantId) {
    var facetRequest = searchRequestMapper.convert(request, INSTANCE_RESOURCE, tenantId);
    return ResponseEntity.ok(facetService.getFacets(facetRequest));
  }

  @Override
  public ResponseEntity<Void> getInstanceIds(String query, String tenantId) {
    var bulkRequest = CqlResourceIdsRequest.of(query, INSTANCE_RESOURCE, tenantId);
    var requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    Assert.notNull(requestAttributes, "Request attributes must be not null");

    var httpServletResponse = requestAttributes.getResponse();
    Assert.notNull(httpServletResponse, "HttpServletResponse must be not null");

    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
    httpServletResponse.setContentType(APPLICATION_JSON_VALUE);

    try {
      resourceIdService.streamResourceIds(bulkRequest, httpServletResponse.getOutputStream());
      return ResponseEntity.ok().build();
    } catch (IOException e) {
      throw new SearchServiceException("Failed to get output stream from response", e);
    }
  }
}
