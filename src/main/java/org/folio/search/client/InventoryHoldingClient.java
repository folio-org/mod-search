package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import java.util.List;
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
  InventoryHoldingDtoCollection getHoldings(@RequestParam("query") CqlQuery cql,
                                            @RequestParam("offset") int offset,
                                            @RequestParam("limit") int limit);

  @GetMapping(path = "/holdings", produces = APPLICATION_JSON_VALUE)
  InventoryHoldingDtoCollection getHoldings(@RequestParam("offset") int offset,
                                            @RequestParam("limit") int limit);

  @GetMapping(path = "/holdings?limit=0&totalRecords=exact", produces = APPLICATION_JSON_VALUE)
  InventoryRecordsCountDto getHoldingsCount();

  record InventoryHoldingDto(String id) {}

  record InventoryHoldingDtoCollection(List<InventoryHoldingDto> holdingsRecords, int totalRecords) {}
}
