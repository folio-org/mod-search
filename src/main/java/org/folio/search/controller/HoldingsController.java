package org.folio.search.controller;

import static org.folio.search.model.service.CqlResourceIdsRequest.HOLDING_ID_PATH;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import lombok.RequiredArgsConstructor;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.rest.resource.HoldingsApi;
import org.folio.search.service.ResourceIdsStreamHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/search")
public class HoldingsController implements HoldingsApi {

  private final ResourceIdsStreamHelper resourceIdsStreamHelper;

  @Override
  public ResponseEntity<Void> getHoldingIds(String query, String tenantId) {
    var bulkRequest = CqlResourceIdsRequest.of(query, INSTANCE_RESOURCE, tenantId, HOLDING_ID_PATH);
    return resourceIdsStreamHelper.streamResourceIds(bulkRequest);
  }
}
