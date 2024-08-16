package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import org.folio.search.model.client.CqlQuery;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("holdings-storage")
public interface InventoryHoldingClient {

  @GetMapping(
    path = "/holdings",
    consumes = APPLICATION_OCTET_STREAM_VALUE,
    produces = APPLICATION_JSON_VALUE)
  InventoryRecordDtoCollection<InventoryHoldingDto> getHoldings(@RequestParam("query") CqlQuery cql,
                                                                @RequestParam("offset") int offset,
                                                                @RequestParam("limit") int limit);

  @GetMapping(path = "/holdings", produces = APPLICATION_JSON_VALUE)
  InventoryRecordDtoCollection<InventoryHoldingDto> getHoldings(
    @RequestParam("limit") int limit, @RequestParam("totalRecords") String totalRecords);

  record InventoryHoldingDto(String id) {}
}
