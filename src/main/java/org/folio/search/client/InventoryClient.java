package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import org.folio.search.model.client.CqlQuery;
import org.folio.search.model.service.ResultList;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient
public interface InventoryClient {

  @GetMapping(
    path = "/instance-storage/instances",
    consumes = APPLICATION_OCTET_STREAM_VALUE,
    produces = APPLICATION_JSON_VALUE)
  ResultList<InventoryInstanceDto> getInstances(@RequestParam("query") CqlQuery cql,
                                               @RequestParam("offset") int offset,
                                               @RequestParam("limit") int limit);

  @GetMapping(path = "/instance-storage/instances", produces = APPLICATION_JSON_VALUE)
  ResultList<InventoryInstanceDto> getInstances(@RequestParam("limit") int limit,
                                                @RequestParam("totalRecords") TotalRecordsType totalRecordsType);

  @GetMapping(
    path = "/item-storage/items",
    consumes = APPLICATION_OCTET_STREAM_VALUE,
    produces = APPLICATION_JSON_VALUE)
  ResultList<InventoryItemDto> getItems(@RequestParam("query") CqlQuery cql,
                                        @RequestParam("offset") int offset,
                                        @RequestParam("limit") int limit);

  @GetMapping(path = "/item-storage/items", produces = APPLICATION_JSON_VALUE)
  ResultList<InventoryItemDto> getItems(@RequestParam("limit") int limit,
                                        @RequestParam("totalRecords") TotalRecordsType totalRecordsType);

  @GetMapping(
    path = "/holdings-storage/holdings",
    consumes = APPLICATION_OCTET_STREAM_VALUE,
    produces = APPLICATION_JSON_VALUE)
  ResultList<InventoryHoldingDto> getHoldings(@RequestParam("query") CqlQuery cql,
                                              @RequestParam("offset") int offset,
                                              @RequestParam("limit") int limit);

  @GetMapping(path = "/holdings-storage/holdings", produces = APPLICATION_JSON_VALUE)
  ResultList<InventoryHoldingDto> getHoldings(@RequestParam("limit") int limit,
                                              @RequestParam("totalRecords") TotalRecordsType totalRecordsType);

  @PostMapping(path = "/inventory-reindex-records/publish", consumes = APPLICATION_JSON_VALUE)
  void publishReindexRecords(ReindexRecords reindexRecords);

  record InventoryInstanceDto(String id) {}

  record InventoryItemDto(String id) {}

  record InventoryHoldingDto(String id) {}

  record ReindexRecords(String id, String recordType, ReindexRecordsRange recordIdsRange) {}

  record ReindexRecordsRange(String from, String to) {}
}
