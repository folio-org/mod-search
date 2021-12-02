package org.folio.search.controller;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthoritySearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.rest.resource.AuthoritiesApi;
import org.folio.search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/search")
public class AuthorityController implements AuthoritiesApi {

  private final SearchService searchService;

  @Override
  public ResponseEntity<AuthoritySearchResult> searchAuthorities(
    String tenant, String query, Integer limit, Integer offset, Boolean expandAll) {
    var searchRequest = CqlSearchRequest.of(Authority.class, tenant, query, limit, offset, expandAll);
    var result = searchService.search(searchRequest);
    return ResponseEntity.ok(new AuthoritySearchResult()
      .authorities(result.getRecords())
      .totalRecords(result.getTotalRecords()));
  }
}
