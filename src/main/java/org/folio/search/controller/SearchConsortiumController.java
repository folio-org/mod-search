package org.folio.search.controller;

import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.BatchIdsDto;
import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.ConsortiumHoldingCollection;
import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.search.domain.dto.ConsortiumItemCollection;
import org.folio.search.domain.dto.ConsortiumLocationCollection;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.SortOrder;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.service.ConsortiumSearchContext;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.model.types.ResourceType;
import org.folio.search.rest.resource.SearchConsortiumApi;
import org.folio.search.service.consortium.ConsortiumInstanceSearchService;
import org.folio.search.service.consortium.ConsortiumInstanceService;
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

  static final String REQUEST_NOT_ALLOWED_MSG =
    "The request allowed only for central tenant of consortium environment";

  private final ConsortiumTenantService consortiumTenantService;
  private final ConsortiumInstanceService instanceService;
  private final ConsortiumLocationService locationService;
  private final ConsortiumInstanceSearchService searchService;

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
  public ResponseEntity<ConsortiumLocationCollection> getConsortiumLocations(String tenantHeader,
                                                                             String tenantId,
                                                                             Integer limit,
                                                                             Integer offset,
                                                                             String sortBy,
                                                                             SortOrder sortOrder) {
    verifyAndGetTenant(tenantHeader);
    var result = locationService.fetchLocations(tenantHeader, tenantId, limit, offset, sortBy, sortOrder);

    return ResponseEntity.ok(new
      ConsortiumLocationCollection()
      .locations(result.getRecords())
      .totalRecords(result.getTotalRecords()));
  }

  @Override
  public ResponseEntity<ConsortiumHolding> getConsortiumHolding(UUID id, String tenantHeader) {
    var tenant = verifyAndGetTenant(tenantHeader);
    var holdingId = id.toString();
    var query = "holdings.id=" + holdingId;
    var searchRequest = CqlSearchRequest.of(Instance.class, tenant, query, 1, 0, true, false, true);

    var result = searchService.getConsortiumHolding(id.toString(), searchRequest);
    return ResponseEntity.ok(result);
  }

  @Override
  public ResponseEntity<ConsortiumItem> getConsortiumItem(UUID itemId, String tenantHeader) {
    var tenant = verifyAndGetTenant(tenantHeader);
    var query = "items.id=" + itemId.toString();
    var searchRequest = CqlSearchRequest.of(Instance.class, tenant, query, 1, 0, true, false, true);

    var result = searchService.getConsortiumItem(itemId.toString(), searchRequest);
    return ResponseEntity.ok(result);
  }

  @Override
  public ResponseEntity<ConsortiumHoldingCollection> fetchConsortiumBatchHoldings(String tenantHeader,
                                                                                  BatchIdsDto batchIdsDto) {
    var tenant = verifyAndGetTenant(tenantHeader);
    var holdingIds = batchIdsDto.getIds().stream().map(UUID::toString).collect(Collectors.toSet());
    var query = batchIdsDto.getIds().stream()
      .map(UUID::toString)
      .map("holdings.id = %s"::formatted)
      .collect(Collectors.joining(" or "));
    var searchRequest = CqlSearchRequest.of(Instance.class, tenant, query, holdingIds.size(), 0, true, false, true);

    var result = searchService.fetchConsortiumBatchHoldings(searchRequest, holdingIds);
    return ResponseEntity.ok(result);
  }

  @Override
  public ResponseEntity<ConsortiumItemCollection> fetchConsortiumBatchItems(String tenantHeader,
                                                                            BatchIdsDto batchIdsDto) {
    if (batchIdsDto.getIds().isEmpty()) {
      return ResponseEntity
        .ok(new ConsortiumItemCollection());
    }

    var tenant = verifyAndGetTenant(tenantHeader);
    var itemIds = batchIdsDto.getIds().stream().map(UUID::toString).collect(Collectors.toSet());
    var query = batchIdsDto.getIds().stream()
      .map(UUID::toString)
      .map("items.id = %s"::formatted)
      .collect(Collectors.joining(" or "));
    var searchRequest = CqlSearchRequest.of(Instance.class, tenant, query, itemIds.size(), 0, true, false, true);

    var result = searchService.fetchConsortiumBatchItems(searchRequest, itemIds);
    return ResponseEntity.ok(result);
  }

  private String verifyAndGetTenant(String tenantHeader) {
    var centralTenant = consortiumTenantService.getCentralTenant(tenantHeader);
    if (centralTenant.isEmpty() || !centralTenant.get().equals(tenantHeader)) {
      throw new RequestValidationException(REQUEST_NOT_ALLOWED_MSG, XOkapiHeaders.TENANT, tenantHeader);
    }
    return centralTenant.get();
  }

}
