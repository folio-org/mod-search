package org.folio.search.model.rest.response;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(staticName = "of")
public class SearchResult implements PagedResult<Map<String, Object>> {

  private final long totalRecords;
  private final List<Map<String, Object>> instances;
}
