package org.folio.search.controller;

import static org.springframework.http.HttpStatus.CREATED;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.ResourceEventBody;
import org.folio.search.model.rest.request.IndexRequestBody;
import org.folio.search.model.rest.response.FolioCreateIndexResponse;
import org.folio.search.model.rest.response.FolioIndexResourceResponse;
import org.folio.search.model.rest.response.FolioPutMappingResponse;
import org.folio.search.service.IndexService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller with set of endpoints for manipulating with Elasticsearch index API.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/index")
public class IndexController {

  private final IndexService elasticsearchIndexService;

  /**
   * Creates indices for passed resource name and tenant id in request header.
   *
   * @param requestBody request body as {@link IndexRequestBody} object.
   * @param tenantId tenant id as {@link String} object.
   * @return {@link FolioCreateIndexResponse} object from elasticsearch.
   */
  @PostMapping("/indices")
  public FolioCreateIndexResponse createIndices(
    @RequestBody IndexRequestBody requestBody,
    @NotEmpty @RequestHeader("tenant-id") String tenantId) {
    return elasticsearchIndexService.createIndex(requestBody.getResourceName(), tenantId);
  }

  /**
   * Updates mappings for resource and tenant id in request header.
   *
   * @param requestBody request body as {@link IndexRequestBody} object.
   * @param tenantId tenant id as {@link String} object.
   * @return {@link FolioCreateIndexResponse} object from elasticsearch.
   */
  @PostMapping("/mappings")
  public FolioPutMappingResponse updateMappings(
    @RequestBody IndexRequestBody requestBody,
    @NotEmpty @RequestHeader("tenant-id") String tenantId) {
    return elasticsearchIndexService.updateMappings(requestBody.getResourceName(), tenantId);
  }

  /**
   * Saves resource to the search engine.
   *
   * @param events resource in event body.
   */
  @PostMapping("/resources")
  @ResponseStatus(CREATED)
  public FolioIndexResourceResponse indexResource(@RequestBody List<ResourceEventBody> events) {
    return elasticsearchIndexService.indexResources(events);
  }
}
