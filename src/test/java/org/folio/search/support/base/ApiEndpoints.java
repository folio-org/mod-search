package org.folio.search.support.base;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.folio.cql2pgjson.model.CqlSort;
import org.folio.search.domain.dto.RecordType;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.Pair;

@UtilityClass
public class ApiEndpoints {

  public static String instanceSearchPath() {
    return "/search/instances";
  }

  public static String consortiumHoldingsSearchPath() {
    return "/search/consortium/holdings";
  }

  public static String consortiumHoldingsSearchPath(List<Pair<String, String>> queryParams) {
    return addQueryParams(consortiumHoldingsSearchPath(), queryParams);
  }

  public static String consortiumItemsSearchPath() {
    return "/search/consortium/items";
  }

  public static String consortiumItemsSearchPath(List<Pair<String, String>> queryParams) {
    return addQueryParams(consortiumItemsSearchPath(), queryParams);
  }

  public static String authoritySearchPath() {
    return "/search/authorities";
  }

  public static String instanceCallNumberBrowsePath() {
    return "/browse/call-numbers/instances";
  }

  public static String instanceSubjectBrowsePath() {
    return "/browse/subjects/instances";
  }

  public static String instanceContributorBrowsePath() {
    return "/browse/contributors/instances";
  }

  public static String authorityBrowsePath() {
    return "/browse/authorities";
  }

  public static String recordFacetsPath(RecordType type, String query, String... facets) {
    var joinedFacets = String.join("&facet=", facets);
    return String.format("/search/%s/facets?query=%s&facet=%s", type.getValue(), query, joinedFacets);
  }

  public static String languageConfigPath() {
    return "/search/config/languages";
  }

  public static String languageConfigPath(String languageCode) {
    return "/search/config/languages/" + languageCode;
  }

  public static String featureConfigPath() {
    return "/search/config/features";
  }

  public static String featureConfigPath(TenantConfiguredFeature feature) {
    return featureConfigPath() + "/" + feature.getValue();
  }

  public static String createIndicesPath() {
    return "/search/index/indices";
  }

  public static String instanceIdsPath(String query) {
    return String.format("/search/instances/ids?query=%s", query);
  }

  public static String resourcesIdsPath(String query) {
    return String.format("/search/resources/jobs/%s/ids", query);
  }

  public static String resourcesIdsJobPath() {
    return "/search/resources/jobs";
  }

  public static String resourcesIdsJobPath(String id) {
    return String.format("/search/resources/jobs/%s", id);
  }

  public static String holdingIdsPath(String query) {
    return String.format("/search/holdings/ids?query=%s", query);
  }

  public static String reindexPath() {
    return "/search/index/inventory/reindex";
  }

  public static String updateIndexSettingsPath() {
    return "/search/index/settings";
  }

  public static String allRecordsSortedBy(String sort, CqlSort order) {
    return String.format("cql.allRecords=1 sortBy %s/sort.%s", sort, order);
  }

  private static String addQueryParams(String path, List<Pair<String, String>> queryParams) {
    var queryParamString = queryParams.stream()
      .map(param -> param.getFirst() + "=" + param.getSecond())
      .collect(Collectors.joining("&"));
    return path + "?" + queryParamString;
  }
}
