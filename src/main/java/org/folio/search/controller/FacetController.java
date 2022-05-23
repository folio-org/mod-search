package org.folio.search.controller;

import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.RecordType;
import org.folio.search.model.service.CqlFacetRequest;
import org.folio.search.rest.resource.RecordTypeApi;
import org.folio.search.service.FacetService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/search")
public class FacetController implements RecordTypeApi {

  private static final Map<RecordType, String> recordTypeToResourceMap = Map.of(
    RecordType.INSTANCES, INSTANCE_RESOURCE,
    RecordType.AUTHORITIES, AUTHORITY_RESOURCE,
    RecordType.CONTRIBUTORS, CONTRIBUTOR_RESOURCE
  );

  private final FacetService facetService;

  @Override
  public ResponseEntity<FacetResult> getFacets(RecordType recordType, String query,
                                               List<String> facet, String tenantId) {
    String recordResource = recordTypeToResourceMap.getOrDefault(recordType, recordType.getValue());
    var facetRequest = CqlFacetRequest.of(recordResource, tenantId, query, facet);
    return ResponseEntity.ok(facetService.getFacets(facetRequest));
  }
}
