package org.folio.search.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.ConsortiumHoldingCollection;
import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.search.domain.dto.ConsortiumItemCollection;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.ConsortiumLocationCollection;
import org.folio.search.domain.dto.SortOrder;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.service.ConsortiumSearchContext;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.model.types.ResourceType;
import org.folio.search.rest.resource.SearchConsortiumApi;
import org.folio.search.service.SearchService;
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
  private final SearchService searchService;

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
  public ResponseEntity<ConsortiumHolding> getConsortiumHolding(UUID id, String tenantHeader) {
    var tenant = verifyAndGetTenant(tenantHeader);
    var query = "holdings.id=" + id.toString();
    var searchRequest = CqlSearchRequest.of(Instance.class, tenant, query, 1, 0, true);
    var result = searchService.search(searchRequest);

    if (CollectionUtils.isEmpty(result.getRecords())
      || CollectionUtils.isEmpty(result.getRecords().iterator().next().getHoldings())) {
      return ResponseEntity.ok(new ConsortiumHolding());
    }

    var instance = result.getRecords().iterator().next();
    var holding = instance.getHoldings().iterator().next();
    return ResponseEntity.ok(new ConsortiumHolding()
      .id(id.toString())
      .tenantId(holding.getTenantId())
      .instanceId(instance.getId())
    );
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
  public ResponseEntity<ConsortiumItem> getConsortiumItem(UUID itemId, String tenantHeader) {
    var tenant = verifyAndGetTenant(tenantHeader);
    var query = "items.id=" + itemId.toString();
    var searchRequest = CqlSearchRequest.of(Instance.class, tenant, query, 1, 0, true);
    var result = searchService.search(searchRequest);

    if (CollectionUtils.isEmpty(result.getRecords())
      || CollectionUtils.isEmpty(result.getRecords().iterator().next().getItems())) {
      return ResponseEntity.ok(new ConsortiumItem());
    }
    var instance = result.getRecords().iterator().next();
    var item = instance.getItems().iterator().next();
    return ResponseEntity.ok(new ConsortiumItem()
      .id(itemId.toString())
      .tenantId(item.getTenantId())
      .instanceId(instance.getId())
      .holdingsRecordId(item.getHoldingsRecordId())
    );
  }

  private String verifyAndGetTenant(String tenantHeader) {
    var centralTenant = consortiumTenantService.getCentralTenant(tenantHeader);
    if (centralTenant.isEmpty() || !centralTenant.get().equals(tenantHeader)) {
      throw new RequestValidationException(REQUEST_NOT_ALLOWED_MSG, XOkapiHeaders.TENANT, tenantHeader);
    }
    return centralTenant.get();
  }

}
