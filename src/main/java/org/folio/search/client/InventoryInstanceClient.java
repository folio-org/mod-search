package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import java.util.List;
import org.folio.search.model.client.CqlQuery;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("instance-storage")
public interface InventoryInstanceClient {

  @GetMapping(
    path = "/instances",
    consumes = APPLICATION_OCTET_STREAM_VALUE,
    produces = APPLICATION_JSON_VALUE)
  InventoryInstanceDtoCollection getInstances(@RequestParam("query") CqlQuery cql,
                                              @RequestParam("offset") int offset,
                                              @RequestParam("limit") int limit);

  @GetMapping(path = "/instances", produces = APPLICATION_JSON_VALUE)
  InventoryInstanceDtoCollection getInstances(@RequestParam("offset") int offset,
                                              @RequestParam("limit") int limit);

  @GetMapping(path = "/instances?limit=0&totalRecords=exact", produces = APPLICATION_JSON_VALUE)
  InventoryRecordsCountDto getInstancesCount();

  record InventoryInstanceDto(String id) {}

  record InventoryInstanceDtoCollection(List<InventoryInstanceDto> instances, int totalRecords) {}
}
