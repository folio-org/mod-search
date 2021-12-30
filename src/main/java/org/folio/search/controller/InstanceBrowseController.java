package org.folio.search.controller;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.CallNumberBrowseResult;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.service.CallNumberBrowseRequest;
import org.folio.search.rest.resource.BrowseApi;
import org.folio.search.service.CallNumberBrowseService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
public class InstanceBrowseController implements BrowseApi {

  private final CallNumberBrowseService callNumberBrowseService;

  @Override
  public ResponseEntity<CallNumberBrowseResult> browseInstancesByCallNumber(String query, String tenant,
    Integer limit, Boolean expandAll, Boolean highlightMatch, Integer precedingRecordsCount) {
    if (precedingRecordsCount != null && precedingRecordsCount > limit) {
      throw new RequestValidationException("Preceding records count must be less than request limit",
        "precedingRecordsCount", String.valueOf(precedingRecordsCount));
    }

    var serviceRequest = CallNumberBrowseRequest.of(INSTANCE_RESOURCE, tenant, query,
      limit, expandAll, highlightMatch, defaultIfNull(precedingRecordsCount, limit / 2));

    var instanceByCallNumber = callNumberBrowseService.browseByCallNumber(serviceRequest);
    return ResponseEntity.ok(new CallNumberBrowseResult()
      .items(instanceByCallNumber.getRecords())
      .totalRecords(instanceByCallNumber.getTotalRecords()));
  }
}
