package org.folio.search.controller;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.RecordType;
import org.folio.search.model.service.CqlFacetRequest;
import org.folio.search.model.types.ResourceType;
import org.folio.search.rest.resource.FacetsApi;
import org.folio.search.service.FacetService;
import org.folio.search.service.consortium.TenantProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class FacetsController implements FacetsApi {

  private static final Map<RecordType, ResourceType> RECORD_TYPE_TO_RESOURCE_MAP = Map.of(
    RecordType.INSTANCES, ResourceType.INSTANCE,
    RecordType.AUTHORITIES, ResourceType.AUTHORITY,
    RecordType.CONTRIBUTORS, ResourceType.INSTANCE_CONTRIBUTOR,
    RecordType.SUBJECTS, ResourceType.INSTANCE_SUBJECT,
    RecordType.CLASSIFICATIONS, ResourceType.INSTANCE_CLASSIFICATION,
    RecordType.CALL_NUMBERS, ResourceType.INSTANCE_CALL_NUMBER
  );

  private final FacetService facetService;
  private final TenantProvider tenantProvider;

  @Override
  public ResponseEntity<FacetResult> getFacets(RecordType recordType, String query,
                                               List<String> facet, String tenantId) {
    var recordResource = RECORD_TYPE_TO_RESOURCE_MAP.getOrDefault(recordType, ResourceType.UNKNOWN);
    tenantId = tenantProvider.getTenant(tenantId);
    var facetRequest = new CqlFacetRequest(recordResource, tenantId, query, facet);
    return ResponseEntity.ok(facetService.getFacets(facetRequest));
  }
}
