package org.folio.search.support.base;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ApiEndpoints {

  public static String searchInstancesByQuery(String query) {
    return String.format("/search/instances?query=%s&limit=%s&offset=%s", query, 100, 0);
  }

  public static String languageConfig() {
    return "/search/config/languages";
  }

  public static String createIndicesEndpoint() {
    return "/search/index/indices";
  }
}
