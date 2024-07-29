package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.search.configuration.StreamingFeignClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "inventory-view-stream", url = "inventory-view",
  configuration = StreamingFeignClientConfiguration.class)
public interface StreamingInventoryViewClient {

  /**
   * Retrieves resources by ids from inventory service.
   *
   * <p>Instances are retrieved as map to collect all fields that can be ignored by mod-search, but still can be
   * required to implement specific features, like searching by all fields.</p>
   */
  @PostMapping(path = "/instances", consumes = APPLICATION_JSON_VALUE)
  Stream<String> getInstances(@RequestBody IdInput ids);

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  class IdInput {
    private List<String> ids;
  }

}
