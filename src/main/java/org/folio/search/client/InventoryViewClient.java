package org.folio.search.client;

import static java.util.Collections.emptyList;
import static org.folio.search.utils.SearchUtils.INSTANCE_HOLDING_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.INSTANCE_ITEM_FIELD_NAME;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.model.client.CqlQuery;
import org.folio.search.model.service.ResultList;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("inventory-view")
public interface InventoryViewClient {

  /**
   * Retrieves resources by ids from inventory service.
   *
   * <p>Instances are retrieved as map to collect all fields that can be ignored by mod-search, but still can be
   * required to implement specific features, like searching by all fields.</p>
   *
   * @param cql - CQL query
   * @param limit - limit of resources to fetch
   * @return {@link ResultList} with Instance objects inside.
   */
  @GetMapping(path = "/instances", consumes = APPLICATION_OCTET_STREAM_VALUE)
  ResultList<InstanceView> getInstances(@RequestParam("query") CqlQuery cql, @RequestParam("limit") int limit);

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  class InstanceView {

    private Map<String, Object> instance;
    private List<Map<String, Object>> holdingsRecords = emptyList();
    private List<Map<String, Object>> items = emptyList();

    /**
     * Converts {@link InstanceView} response to search instance representation as {@link Map} object.
     *
     * @return {@link Map} with fields for search indexing process.
     */
    public Map<String, Object> toInstance() {
      if (CollectionUtils.isNotEmpty(items)) {
        instance.put(INSTANCE_ITEM_FIELD_NAME, items);
      }

      if (CollectionUtils.isNotEmpty(holdingsRecords)) {
        instance.put(INSTANCE_HOLDING_FIELD_NAME, holdingsRecords);
      }

      return instance;
    }
  }
}
