package org.folio.search.support.base;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.folio.cql2pgjson.model.CqlSort;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
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

  public static String consortiumLocationsSearchPath() {
    return "/search/consortium/locations";
  }

  public static String consortiumLocationsSearchPath(List<Pair<String, String>> queryParams) {
    return addQueryParams(consortiumLocationsSearchPath(), queryParams);
  }

  public static String consortiumCampusesSearchPath() {
    return "/search/consortium/campuses";
  }

  public static String consortiumCampusesSearchPath(List<Pair<String, String>> queryParams) {
    return addQueryParams(consortiumCampusesSearchPath(), queryParams);
  }

  public static String consortiumLibrariesSearchPath() {
    return "/search/consortium/libraries";
  }

  public static String consortiumLibrariesSearchPath(List<Pair<String, String>> queryParams) {
    return addQueryParams(consortiumLibrariesSearchPath(), queryParams);
  }

  public static String consortiumInstitutionsSearchPath() {
    return "/search/consortium/institutions";
  }

  public static String consortiumInstitutionsSearchPath(List<Pair<String, String>> queryParams) {
    return addQueryParams(consortiumInstitutionsSearchPath(), queryParams);
  }

  public static String consortiumItemsSearchPath() {
    return "/search/consortium/items";
  }

  public static String consortiumItemsSearchPath(List<Pair<String, String>> queryParams) {
    return addQueryParams(consortiumItemsSearchPath(), queryParams);
  }

  public static String consortiumHoldingSearchPath(String id) {
    return "/search/consortium/holding/" + id;
  }

  public static String consortiumItemSearchPath(String id) {
    return "/search/consortium/item/" + id;
  }

  public static String consortiumBatchHoldingsSearchPath() {
    return "/search/consortium/batch/holdings";
  }

  public static String consortiumBatchItemsSearchPath() {
    return "/search/consortium/batch/items";
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

  public static String instanceClassificationBrowsePath(BrowseOptionType optionType) {
    return "/browse/classification-numbers/" + optionType.getValue() + "/instances";
  }

  public static String linkedDataInstanceSearchPath() {
    return "/search/linked-data/instances";
  }

  public static String linkedDataWorkSearchPath() {
    return "/search/linked-data/works";
  }

  public static String linkedDataHubSearchPath() {
    return "/search/linked-data/hubs";
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

  public static String browseConfigPath(BrowseType type) {
    return "/browse/config/" + type.getValue();
  }

  public static String browseConfigPath(BrowseType type, BrowseOptionType optionType) {
    return "/browse/config/" + type.getValue() + "/" + optionType.getValue();
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

  public static String reindexInstanceRecordsStatus() {
    return "/search/index/instance-records/reindex/status";
  }

  public static String reindexFullPath() {
    return "/search/index/instance-records/reindex/full";
  }

  public static String reindexUploadPath() {
    return "/search/index/instance-records/reindex/upload";
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
