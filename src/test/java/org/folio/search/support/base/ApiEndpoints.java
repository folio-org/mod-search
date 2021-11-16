package org.folio.search.support.base;

import lombok.experimental.UtilityClass;
import org.folio.search.domain.dto.TenantConfiguredFeature;

@UtilityClass
public class ApiEndpoints {

  public static String instanceSearchPath() {
    return "/search/instances";
  }

  public static String authoritySearchPath() {
    return "/search/authorities";
  }

  public static String instanceFacets(String query, String... facets) {
    var joinedFacets = String.join("&facet=", facets);
    return String.format("/search/instances/facets?query=%s&facet=%s", query, joinedFacets);
  }

  public static String languageConfig() {
    return "/search/config/languages";
  }

  public static String featureConfig() {
    return "/search/config/features";
  }

  public static String featureConfig(TenantConfiguredFeature feature) {
    return featureConfig() + "/" + feature.getValue();
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

  public static String authorityIds(String query) {
    return String.format("/search/authorities/ids?query=%s", query);
  }
}
