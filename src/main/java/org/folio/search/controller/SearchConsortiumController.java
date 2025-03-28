package org.folio.search.controller;

import static org.folio.search.utils.SearchResponseHelper.REQUEST_NOT_ALLOWED_MSG;
import static org.folio.search.utils.SearchUtils.INSTANCE_HOLDING_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.INSTANCE_ITEM_FIELD_NAME;

import java.util.HashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.BatchIdsDto;
import org.folio.search.domain.dto.ConsortiumCampusCollection;
import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.ConsortiumHoldingCollection;
import org.folio.search.domain.dto.ConsortiumInstitutionCollection;
import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.search.domain.dto.ConsortiumItemCollection;
import org.folio.search.domain.dto.ConsortiumLibraryCollection;
import org.folio.search.domain.dto.ConsortiumLocationCollection;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.SortOrder;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.service.ConsortiumSearchContext;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.model.types.ResourceType;
import org.folio.search.rest.resource.SearchConsortiumApi;
import org.folio.search.service.consortium.ConsortiumCampusService;
import org.folio.search.service.consortium.ConsortiumInstanceSearchService;
import org.folio.search.service.consortium.ConsortiumInstanceService;
import org.folio.search.service.consortium.ConsortiumInstitutionService;
import org.folio.search.service.consortium.ConsortiumLibraryService;
import org.folio.search.service.consortium.ConsortiumLocationService;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.spring.integration.XOkapiHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class SearchConsortiumController implements SearchConsortiumApi {

  private final ConsortiumTenantService consortiumTenantService;
  private final ConsortiumInstanceService instanceService;
  private final ConsortiumLocationService locationService;
  private final ConsortiumInstanceSearchService searchService;
  private final ConsortiumCampusService campusService;
  private final ConsortiumLibraryService libraryService;
  private final ConsortiumInstitutionService institutionService;

  @Override
  public ResponseEntity<ConsortiumHoldingCollection> fetchConsortiumBatchHoldings(String tenantHeader,
                                                                                  BatchIdsDto batchIdsDto) {

    if (batchIdsDto.getIdentifierValues().isEmpty()) {
      return ResponseEntity.ok(new ConsortiumHoldingCollection());
    }

    var tenant = verifyAndGetTenant(tenantHeader);
    var identifierType = batchIdsDto.getIdentifierType();
    var identifierValues = new HashSet<>(batchIdsDto.getIdentifierValues());

    var result = searchService.fetchConsortiumBatchHoldings(tenant, identifierValues, identifierType);
    return ResponseEntity.ok(result);
  }

  @Override
  public ResponseEntity<ConsortiumItemCollection> fetchConsortiumBatchItems(String tenantHeader,
                                                                            BatchIdsDto batchIdsDto) {
    if (batchIdsDto.getIdentifierValues().isEmpty()) {
      return ResponseEntity.ok(new ConsortiumItemCollection());
    }

    var tenant = verifyAndGetTenant(tenantHeader);
    var identifierType = batchIdsDto.getIdentifierType();
    var identifierValues = new HashSet<>(batchIdsDto.getIdentifierValues());

    var result = searchService.fetchConsortiumBatchItems(tenant, identifierValues, identifierType);
    return ResponseEntity.ok(result);
  }

  @Override
  public ResponseEntity<ConsortiumCampusCollection> getConsortiumCampuses(String tenantHeader,
                                                                          String tenantId,
                                                                          String id,
                                                                          Integer limit,
                                                                          Integer offset,
                                                                          String sortBy,
                                                                          SortOrder sortOrder) {
    var result = campusService.fetchCampuses(tenantHeader, tenantId, id, limit, offset, sortBy, sortOrder);

    return ResponseEntity.ok(new
      ConsortiumCampusCollection()
      .campuses(result.getRecords())
      .totalRecords(result.getTotalRecords()));
  }

  @Override
  public ResponseEntity<ConsortiumHolding> getConsortiumHolding(UUID id, String tenantHeader) {
    var tenant = verifyAndGetTenant(tenantHeader);
    var holdingId = id.toString();
    var searchRequest = idCqlRequest(tenant, INSTANCE_HOLDING_FIELD_NAME, holdingId);

    var result = searchService.getConsortiumHolding(holdingId, searchRequest);
    return ResponseEntity.ok(result);
  }

  @Override
  public ResponseEntity<ConsortiumHoldingCollection> getConsortiumHoldings(String tenantHeader, String instanceId,
                                                                           String tenantId, Integer limit,
                                                                           Integer offset, String sortBy,
                                                                           SortOrder sortOrder) {
    verifyAndGetTenant(tenantHeader);
    var context = ConsortiumSearchContext.builderFor(ResourceType.HOLDINGS)
      .filter("instanceId", instanceId)
      .filter("tenantId", tenantId)
      .limit(limit)
      .offset(offset)
      .sortBy(sortBy)
      .sortOrder(sortOrder)
      .build();
    return ResponseEntity.ok(instanceService.fetchHoldings(context));
  }

  @Override
  public ResponseEntity<ConsortiumInstitutionCollection> getConsortiumInstitutions(String tenantHeader,
                                                                                   String tenantId,
                                                                                   String id,
                                                                                   Integer limit,
                                                                                   Integer offset,
                                                                                   String sortBy,
                                                                                   SortOrder sortOrder) {
    var result = institutionService.fetchInstitutions(tenantHeader, tenantId, id, limit, offset, sortBy, sortOrder);

    return ResponseEntity.ok(new
      ConsortiumInstitutionCollection()
      .institutions(result.getRecords())
      .totalRecords(result.getTotalRecords()));
  }

  @Override
  public ResponseEntity<ConsortiumItem> getConsortiumItem(UUID id, String tenantHeader) {
    var tenant = verifyAndGetTenant(tenantHeader);
    var itemId = id.toString();
    var searchRequest = idCqlRequest(tenant, INSTANCE_ITEM_FIELD_NAME, itemId);

    var result = searchService.getConsortiumItem(itemId, searchRequest);
    return ResponseEntity.ok(result);
  }

  @Override
  public ResponseEntity<ConsortiumItemCollection> getConsortiumItems(String tenantHeader, String instanceId,
                                                                     String holdingsRecordId, String tenantId,
                                                                     Integer limit, Integer offset, String sortBy,
                                                                     SortOrder sortOrder) {
    verifyAndGetTenant(tenantHeader);
    var context = ConsortiumSearchContext.builderFor(ResourceType.ITEM)
      .filter("instanceId", instanceId)
      .filter("tenantId", tenantId)
      .filter("holdingsRecordId", holdingsRecordId)
      .limit(limit)
      .offset(offset)
      .sortBy(sortBy)
      .sortOrder(sortOrder)
      .build();
    return ResponseEntity.ok(instanceService.fetchItems(context));
  }

  @Override
  public ResponseEntity<ConsortiumLibraryCollection> getConsortiumLibraries(String tenantHeader,
                                                                            String tenantId,
                                                                            String id,
                                                                            Integer limit,
                                                                            Integer offset,
                                                                            String sortBy,
                                                                            SortOrder sortOrder) {
    var result = libraryService.fetchLibraries(tenantHeader, tenantId, id, limit, offset, sortBy, sortOrder);

    return ResponseEntity.ok(new
      ConsortiumLibraryCollection()
      .libraries(result.getRecords())
      .totalRecords(result.getTotalRecords()));
  }

  @Override
  public ResponseEntity<ConsortiumLocationCollection> getConsortiumLocations(String tenantHeader,
                                                                             String tenantId,
                                                                             String id,
                                                                             Integer limit,
                                                                             Integer offset,
                                                                             String sortBy,
                                                                             SortOrder sortOrder) {
    var result = locationService.fetchLocations(tenantHeader, tenantId, id, limit, offset, sortBy, sortOrder);

    return ResponseEntity.ok(new
      ConsortiumLocationCollection()
      .locations(result.getRecords())
      .totalRecords(result.getTotalRecords()));
  }

  private String verifyAndGetTenant(String tenantHeader) {
    var centralTenant = consortiumTenantService.getCentralTenant(tenantHeader);
    if (centralTenant.isEmpty() || !centralTenant.get().equals(tenantHeader)) {
      throw new RequestValidationException(REQUEST_NOT_ALLOWED_MSG, XOkapiHeaders.TENANT, tenantHeader);
    }
    return centralTenant.get();
  }

  private CqlSearchRequest<Instance> idCqlRequest(String tenant, String fieldName, String id) {
    var query = fieldName + ".id=" + id;
    return CqlSearchRequest.of(Instance.class, tenant, query, 1, 0, true, false, true);
  }
}
