package org.folio.search.support.base;

import lombok.experimental.UtilityClass;
import org.folio.cql2pgjson.model.CqlSort;

@UtilityClass
public class ApiEndpoints {

  public static String instanceSearchPath() {
    return "/search/instances";
  }

  public static String searchInstancesByQuery(String query, Object... args) {
    final var formattedQuery = String.format(query, args);
    return String.format("/search/instances?query=%s&limit=%s&offset=%s", formattedQuery, 100, 0);
  }

  public static String allInstancesSortedBy(String sort, CqlSort order) {
    return searchInstancesByQuery("cql.allRecords = 1 sortBy %s/sort.%s", sort, order);
  }

  public static String getFacets(String query, String... facets) {
    var joinedFacets = String.join("&facet=", facets);
    return String.format("/search/instances/facets?query=%s&facet=%s", query, joinedFacets);
  }

  public static String languageConfig() {
    return "/search/config/languages";
  }

  public static String createIndicesEndpoint() {
    return "/search/index/indices";
  }

  public static String instanceIds(String query) {
    return String.format("/search/instances/ids?query=%s", query);
  }

  public static String holdingIds(String query) {
    return String.format("/search/holdings/ids?query=%s", query);
  }
}
