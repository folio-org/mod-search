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
    return recordFacets("instances", query, facets);
  }

  public static String authorityFacets(String query, String... facets) {
    return recordFacets("authorities", query, facets);
  }

  public static String recordFacets(String type, String query, String... facets) {
    var joinedFacets = String.join("&facet=", facets);
    return String.format("/search/%s/facets?query=%s&facet=%s", type, query, joinedFacets);
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

  public static String reindexPath() {
    return "/search/index/inventory/reindex";
  }
}
