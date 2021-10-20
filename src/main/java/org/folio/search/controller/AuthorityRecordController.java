package org.folio.search.controller;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.AuthorityRecord;
import org.folio.search.domain.dto.AuthorityRecordSearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.rest.resource.AuthorityRecordsApi;
import org.folio.search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/search")
public class AuthorityRecordController implements AuthorityRecordsApi {

  private final SearchService searchService;

  @Override
  public ResponseEntity<AuthorityRecordSearchResult> searchAuthorityRecords(
    String tenant, String query, Integer limit, Integer offset, Boolean expandAll) {
    var searchRequest = CqlSearchRequest.of(AuthorityRecord.class, tenant, query, limit, offset, expandAll);
    var result = searchService.search(searchRequest);
    return ResponseEntity.ok(new AuthorityRecordSearchResult()
      .authorityRecords(result.getRecords())
      .totalRecords(result.getTotalRecords()));
  }
}
