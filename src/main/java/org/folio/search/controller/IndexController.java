package org.folio.search.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.IndexRequestBody;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.rest.resource.IndexApi;
import org.folio.search.service.IndexService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller with set of endpoints for manipulating with Elasticsearch index API.
 */
@Log4j2
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/search")
public class IndexController implements IndexApi {

  private final IndexService indexService;

  @Override
  public ResponseEntity<FolioCreateIndexResponse> createIndices(String tenantId, IndexRequestBody request) {
    return ResponseEntity.ok(indexService.createIndex(request.getResourceName(), tenantId));
  }

  @Override
  public ResponseEntity<FolioIndexOperationResponse> updateMappings(String tenantId, IndexRequestBody request) {
    return ResponseEntity.ok(indexService.updateMappings(request.getResourceName(), tenantId));
  }

  @Override
  public ResponseEntity<FolioIndexOperationResponse> indexRecords(List<ResourceEventBody> events) {
    log.info("Saving records into elasticsearch [amount of records: {}]", events.size());
    return ResponseEntity.status(HttpStatus.CREATED).body(indexService.indexResources(events));
  }
}
