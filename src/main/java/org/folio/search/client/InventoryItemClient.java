package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import java.util.List;
import org.folio.search.model.client.CqlQuery;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("item-storage")
public interface InventoryItemClient {

  @GetMapping(
    path = "/items",
    consumes = APPLICATION_OCTET_STREAM_VALUE,
    produces = APPLICATION_JSON_VALUE)
  InventoryItemDtoCollection getItems(@RequestParam("query") CqlQuery cql,
                                      @RequestParam("offset") int offset,
                                      @RequestParam("limit") int limit);

  @GetMapping(path = "/items", produces = APPLICATION_JSON_VALUE)
  InventoryItemDtoCollection getItems(@RequestParam("offset") int offset,
                                      @RequestParam("limit") int limit);

  @GetMapping(path = "/items?limit=0&totalRecords=exact", produces = APPLICATION_JSON_VALUE)
  InventoryRecordsCountDto getItemsCount();

  record InventoryItemDto(String id) {}

  record InventoryItemDtoCollection(List<InventoryItemDto> items, int totalRecords) {}
}
