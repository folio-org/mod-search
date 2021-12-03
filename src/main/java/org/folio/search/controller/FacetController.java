package org.folio.search.controller;

import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.RecordType;
import org.folio.search.model.service.CqlFacetRequest;
import org.folio.search.rest.resource.RecordTypeApi;
import org.folio.search.service.FacetService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/{recordType}")
public class FacetController implements RecordTypeApi {

  private final FacetService facetService;

  @Override
  public ResponseEntity<FacetResult> getFacets(@PathVariable RecordType recordType, String query,
                                               List<String> facet, String tenantId) {
    String recordResource = recordType == RecordType.INSTANCES ? INSTANCE_RESOURCE : AUTHORITY_RESOURCE;
    var facetRequest = CqlFacetRequest.of(recordResource, tenantId, query, facet);
    return ResponseEntity.ok(facetService.getFacets(facetRequest));
  }
}
