package org.folio.search.support.base;

import lombok.experimental.UtilityClass;
import org.folio.cql2pgjson.model.CqlSort;

@UtilityClass
public class ApiEndpoints {
  public static String searchInstancesByQuery(String query, Object ... args) {
    final var formattedQuery = String.format(query, args);
    return String.format("/search/instances?query=%s&limit=%s&offset=%s", formattedQuery, 100, 0);
  }

  public static String allInstancesSortedBy(String sort, CqlSort order) {
    return searchInstancesByQuery("id<>\"\" sortBy %s/sort.%s", sort, order);
  }
}
