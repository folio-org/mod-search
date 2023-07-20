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
import org.folio.search.rest.resource.FacetsApi;
import org.folio.search.service.FacetService;
import org.folio.search.service.consortia.TenantProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class FacetsController implements FacetsApi {

  private static final Map<RecordType, String> RECORD_TYPE_TO_RESOURCE_MAP = Map.of(
    RecordType.INSTANCES, INSTANCE_RESOURCE,
    RecordType.AUTHORITIES, AUTHORITY_RESOURCE,
    RecordType.CONTRIBUTORS, CONTRIBUTOR_RESOURCE
  );

  private final FacetService facetService;
  private final TenantProvider tenantProvider;

  @Override
  public ResponseEntity<FacetResult> getFacets(RecordType recordType, String query,
                                               List<String> facet, String tenantId) {
    var recordResource = RECORD_TYPE_TO_RESOURCE_MAP.getOrDefault(recordType, recordType.getValue());
    tenantId = tenantProvider.getTenant(tenantId);
    var facetRequest = CqlFacetRequest.of(recordResource, tenantId, query, facet);
    return ResponseEntity.ok(facetService.getFacets(facetRequest));
  }
}
