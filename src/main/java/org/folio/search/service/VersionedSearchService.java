package org.folio.search.service;

import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.folio.search.utils.SearchUtils.buildPreferenceKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchQueryConfigurationProperties;
import org.folio.search.cql.flat.FlatSearchQueryConverter;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.model.service.QueryResolution;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.consortium.FlatConsortiumSearchHelper;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.search.service.setter.SearchResponsePostProcessor;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

/**
 * Version-aware search entry point. LEGACY delegates to SearchService.
 * FLAT uses FlatSearchQueryConverter + explicit-index SearchRepository overload + hydration.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class VersionedSearchService {

  private final SearchService searchService;
  private final QueryVersionResolver queryVersionResolver;
  private final SearchRepository searchRepository;
  private final ElasticsearchDocumentConverter documentConverter;
  private final FlatSearchQueryConverter flatSearchQueryConverter;
  private final SearchQueryConfigurationProperties searchQueryConfiguration;
  private final SearchPreferenceService searchPreferenceService;
  private final FlatConsortiumSearchHelper flatConsortiumSearchHelper;
  private final Map<Class<?>, SearchResponsePostProcessor<?>> searchResponsePostProcessors;

  public <T> SearchResult<T> search(CqlSearchRequest<T> request, String queryVersion) {
    var resolution = queryVersionResolver.resolve(queryVersion, request.getTenantId());

    if (resolution.pathType() == QueryResolution.PathType.LEGACY) {
      log.debug("search:: delegating to legacy SearchService [version: {}]", queryVersion);
      return searchService.search(request);
    }

    log.debug("search:: using flat search path [version: {}, alias: {}]", queryVersion, resolution.indexName());
    return executeFlatSearch(request, resolution);
  }

  @SuppressWarnings("unchecked")
  private <T> SearchResult<T> executeFlatSearch(CqlSearchRequest<T> request, QueryResolution resolution) {
    // Max-window validation (same as legacy SearchService)
    if (request.getOffset() + request.getLimit() > SearchService.DEFAULT_MAX_SEARCH_RESULT_WINDOW) {
      throw new RequestValidationException("The sum of limit and offset should not exceed 10000.",
        "offset + limit", String.valueOf(request.getOffset() + request.getLimit()));
    }

    var queryBuilder = flatSearchQueryConverter.convert(request.getQuery(), request.getResource());

    // Apply consortium filtering for active affiliation
    var flatQuery = queryBuilder.query();
    if (isFalse(request.getConsortiumConsolidated())) {
      flatQuery = flatConsortiumSearchHelper.addConsortiumFilter(flatQuery, request.getTenantId());
    }

    // Wrap with resourceType=instance filter so primary query only returns instance docs
    var filteredQuery = new BoolQueryBuilder()
      .must(flatQuery)
      .filter(QueryBuilders.termQuery("resourceType", "instance"));
    queryBuilder.query(filteredQuery);

    var requestTimeout = searchQueryConfiguration.getRequestTimeout();
    queryBuilder.from(request.getOffset())
      .size(request.getLimit())
      .trackTotalHits(true)
      .timeout(new TimeValue(requestTimeout.toMillis(), MILLISECONDS));

    var preferenceKey = buildPreferenceKey(request.getTenantId(), request.getResource().getName(), request.getQuery());
    var preference = searchPreferenceService.getPreferenceForString(preferenceKey);

    var searchResponse = searchRepository.search(resolution.indexName(), queryBuilder, preference);
    var hits = searchResponse.getHits();
    var totalRecords = hits.getTotalHits() != null ? (int) hits.getTotalHits().value : 0;

    if (hits.getHits() == null || hits.getHits().length == 0) {
      return SearchResult.of(totalRecords, emptyList());
    }

    var instanceIds = Arrays.stream(hits.getHits())
      .map(hit -> (String) hit.getSourceAsMap().get("id"))
      .filter(Objects::nonNull)
      .toList();

    Map<String, List<Map<String, Object>>> holdingsByInstanceId = new LinkedHashMap<>();
    Map<String, List<Map<String, Object>>> itemsByInstanceId = new LinkedHashMap<>();

    if (!instanceIds.isEmpty() && !isFalse(request.getExpandAll())) {
      fetchChildDocuments(resolution.indexName(), instanceIds, "holding", null, holdingsByInstanceId,
        request.getTenantId(), request.getConsortiumConsolidated());
      fetchChildDocuments(resolution.indexName(), instanceIds, "item", null, itemsByInstanceId,
        request.getTenantId(), request.getConsortiumConsolidated());
    }

    var records = new ArrayList<T>();
    for (var hit : hits.getHits()) {
      var sourceMap = new LinkedHashMap<>(hit.getSourceAsMap());
      var instanceId = (String) sourceMap.get("id");

      normalizeFlatSourceMap(sourceMap);
      sourceMap.put("holdings", holdingsByInstanceId.getOrDefault(instanceId, emptyList()));
      sourceMap.put("items", itemsByInstanceId.getOrDefault(instanceId, emptyList()));

      var record = (T) documentConverter.convert(sourceMap, request.getResourceClass());
      records.add(record);
    }

    var searchResult = SearchResult.of(totalRecords, records);

    searchResultPostProcessing(request.getResourceClass(), request.getIncludeNumberOfTitles(), searchResult);

    return searchResult;
  }

  @SuppressWarnings("unchecked")
  private <T> void searchResultPostProcessing(Class<?> resourceClass, boolean includeNumberOfTitles,
                                              SearchResult<T> searchResult) {
    if (Objects.isNull(resourceClass)) {
      return;
    }
    var postProcessor = searchResponsePostProcessors.get(resourceClass);
    if (Objects.nonNull(postProcessor) && includeNumberOfTitles) {
      postProcessor.process((List) searchResult.getRecords());
    }
  }

  private void fetchChildDocuments(String indexName, List<String> instanceIds, String resourceType,
                                   String[] includeFields,
                                   Map<String, List<Map<String, Object>>> resultByInstanceId,
                                   String tenantId, Boolean consortiumConsolidated) {
    var maxChildren = searchQueryConfiguration.getMaxChildrenFetch();

    var baseQuery = new BoolQueryBuilder()
      .filter(QueryBuilders.termsQuery("instanceId", instanceIds))
      .filter(QueryBuilders.termQuery("resourceType", resourceType));

    var query = isFalse(consortiumConsolidated)
      ? flatConsortiumSearchHelper.addConsortiumFilter(baseQuery, tenantId)
      : baseQuery;

    var searchSource = new SearchSourceBuilder()
      .query(query)
      .size(maxChildren)
      .sort("_doc");

    if (includeFields != null) {
      searchSource.fetchSource(includeFields, null);
    }

    var childCount = new long[]{0};

    searchRepository.streamDocuments(indexName, searchSource, hits -> {
      for (SearchHit hit : hits) {
        childCount[0]++;
        var source = hit.getSourceAsMap();
        var instanceId = (String) source.get("instanceId");
        if (instanceId != null) {
          var cleanSource = normalizeFlatChildSourceMap(source, resourceType);

          resultByInstanceId.computeIfAbsent(instanceId, k -> new ArrayList<>()).add(cleanSource);
        }
      }
    });

    if (childCount[0] > maxChildren) {
      log.warn("fetchChildDocuments:: large number of child documents fetched [resourceType: {}, count: {}]",
        resourceType, childCount[0]);
    }
  }

  @SuppressWarnings("unchecked")
  private static void normalizeFlatSourceMap(Map<String, Object> sourceMap) {
    // Unwrap namespaced instance fields into the top-level source map
    var instanceFields = sourceMap.remove("instance");
    if (instanceFields instanceof Map<?, ?> instanceMap) {
      sourceMap.putAll((Map<String, Object>) instanceMap);
    }

    // Remove flat-index-only fields
    sourceMap.remove("resourceType");
    sourceMap.remove("join_field");
    sourceMap.remove("instanceId");
    sourceMap.remove("sort_title");
    sourceMap.remove("title_sort");
    sourceMap.remove("sort_contributors");
    sourceMap.remove("normalizedDate1");

    // Multi-lang title → plain string
    if (sourceMap.containsKey("plain_title")) {
      sourceMap.put("title", sourceMap.remove("plain_title"));
    }

    // Multi-lang administrativeNotes → plain list
    if (sourceMap.containsKey("plain_administrativeNotes")) {
      sourceMap.put("administrativeNotes", sourceMap.remove("plain_administrativeNotes"));
    }

    // Normalize contributors: use plain_name for name
    var contributors = sourceMap.get("contributors");
    if (contributors instanceof List<?> list) {
      for (var item : list) {
        if (item instanceof Map<?, ?> map) {
          var mutableMap = (Map<String, Object>) map;
          if (mutableMap.containsKey("plain_name")) {
            mutableMap.put("name", mutableMap.remove("plain_name"));
          }
        }
      }
    }

    // Normalize publication: use plain_publisher for publisher
    var publication = sourceMap.get("publication");
    if (publication instanceof List<?> list) {
      for (var item : list) {
        if (item instanceof Map<?, ?> map) {
          var mutableMap = (Map<String, Object>) map;
          if (mutableMap.containsKey("plain_publisher")) {
            mutableMap.put("publisher", mutableMap.remove("plain_publisher"));
          }
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> normalizeFlatChildSourceMap(Map<String, Object> sourceMap, String resourceType) {
    // Unwrap namespaced fields from holding/item docs into API-compatible child payloads.
    var namespacedFields = sourceMap.get(resourceType);
    var cleanSource = namespacedFields instanceof Map<?, ?>
      ? new LinkedHashMap<>((Map<String, Object>) namespacedFields)
      : new LinkedHashMap<String, Object>();

    cleanSource.put("id", sourceMap.get("id"));

    var tenantId = sourceMap.get("tenantId");
    if (tenantId != null) {
      cleanSource.put("tenantId", tenantId);
    }

    var discoverySuppressKey = switch (resourceType) {
      case "holding" -> "holdingsDiscoverySuppress";
      case "item" -> "itemDiscoverySuppress";
      default -> null;
    };
    if (discoverySuppressKey != null && cleanSource.containsKey(discoverySuppressKey)) {
      cleanSource.put("discoverySuppress", Boolean.TRUE.equals(cleanSource.get(discoverySuppressKey)));
      cleanSource.remove(discoverySuppressKey);
    } else if (!cleanSource.containsKey("discoverySuppress")) {
      cleanSource.put("discoverySuppress", false);
    }

    return cleanSource;
  }
}
