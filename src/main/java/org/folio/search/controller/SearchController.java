package org.folio.search.controller;

import static java.lang.Boolean.TRUE;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthoritySearchResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceSearchResult;
import org.folio.search.domain.dto.LinkedDataHub;
import org.folio.search.domain.dto.LinkedDataHubSearchResult;
import org.folio.search.domain.dto.LinkedDataInstance;
import org.folio.search.domain.dto.LinkedDataInstanceSearchResult;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.domain.dto.LinkedDataWorkSearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.rest.resource.SearchApi;
import org.folio.search.service.SearchService;
import org.folio.search.service.consortium.TenantProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class SearchController implements SearchApi {

  private final SearchService searchService;
  private final TenantProvider tenantProvider;

  @Override
  public ResponseEntity<AuthoritySearchResult> searchAuthorities(String tenant, String query, Integer limit,
                                                                 Integer offset, Boolean expandAll,
                                                                 Boolean includeNumberOfTitles, String include) {

    tenant = tenantProvider.getTenant(tenant);
    var searchRequest = CqlSearchRequest.of(Authority.class, tenant, query, limit, offset, expandAll,
      includeNumberOfTitles, include);
    var result = searchService.search(searchRequest);
    return ResponseEntity.ok(new AuthoritySearchResult()
      .authorities(result.getRecords())
      .totalRecords(result.getTotalRecords()));
  }

  @Override
  public ResponseEntity<InstanceSearchResult> searchInstances(String tenantId, String query, Integer limit,
                                                              Integer offset, Boolean expandAll, String include) {
    tenantId = tenantProvider.getTenant(tenantId);
    var searchRequest = CqlSearchRequest.of(Instance.class, tenantId, query, limit, offset, expandAll, include);
    var result = searchService.search(searchRequest);
    return ResponseEntity.ok(new InstanceSearchResult()
      .instances(result.getRecords())
      .totalRecords(result.getTotalRecords()));
  }

  @Override
  public ResponseEntity<LinkedDataInstanceSearchResult> searchLinkedDataInstances(String tenantId,
                                                                                  String query,
                                                                                  Integer limit,
                                                                                  Integer offset) {
    var searchRequest = CqlSearchRequest.of(LinkedDataInstance.class, tenantId, query, limit, offset, true, null);
    var result = searchService.search(searchRequest);
    return ResponseEntity.ok(new LinkedDataInstanceSearchResult()
      .searchQuery(query)
      .content(result.getRecords())
      .pageNumber(divPlusOneIfRemainder(offset, limit))
      .totalPages(divPlusOneIfRemainder(result.getTotalRecords(), limit))
      .totalRecords(result.getTotalRecords())
    );
  }

  @Override
  public ResponseEntity<LinkedDataWorkSearchResult> searchLinkedDataWorks(String tenantId,
                                                                          String query,
                                                                          Integer limit,
                                                                          Integer offset,
                                                                          Boolean omitInstances) {
    var searchRequest = CqlSearchRequest.of(LinkedDataWork.class, tenantId, query, limit, offset, true, null);
    var result = searchService.search(searchRequest);
    if (TRUE.equals(omitInstances)) {
      result.getRecords().forEach(ldw -> ldw.setInstances(null));
    }
    return ResponseEntity.ok(new LinkedDataWorkSearchResult()
      .searchQuery(query)
      .content(result.getRecords())
      .pageNumber(divPlusOneIfRemainder(offset, limit))
      .totalPages(divPlusOneIfRemainder(result.getTotalRecords(), limit))
      .totalRecords(result.getTotalRecords())
    );
  }

  @Override
  public ResponseEntity<LinkedDataHubSearchResult> searchLinkedDataHubs(String tenantId,
                                                                        String query,
                                                                        Integer limit,
                                                                        Integer offset) {
    var searchRequest = CqlSearchRequest.of(
      LinkedDataHub.class, tenantId, query, limit, offset, true, null);
    var result = searchService.search(searchRequest);
    return ResponseEntity.ok(new LinkedDataHubSearchResult()
      .searchQuery(query)
      .content(result.getRecords())
      .pageNumber(divPlusOneIfRemainder(offset, limit))
      .totalPages(divPlusOneIfRemainder(result.getTotalRecords(), limit))
      .totalRecords(result.getTotalRecords())
    );
  }

  private int divPlusOneIfRemainder(int one, int two) {
    var modulo = one % two;
    return one / two + (modulo > 0 ? 1 : 0);
  }
}
