package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import java.util.Map;
import org.folio.search.model.service.ResultList;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("campuses")
public interface CampusesClient {

  /**
   * Retrieves campuses from inventory storage.
   *
   * @param offset - number of resources to skip
   * @param limit - limit of resources to fetch
   * @return {@link ResultList} with Instance objects inside.
   */
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  CampusesResult getCampuses(@RequestParam("offset") int offset, @RequestParam("limit") int limit);

  record CampusesResult(List<Map<String, Object>> campuses, int totalRecords) { }
}
