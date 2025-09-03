package org.folio.search.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.folio.search.utils.SearchUtils.buildPreferenceKey;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchQueryConfigurationProperties;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.service.setter.SearchResponsePostProcessor;
import org.opensearch.common.unit.TimeValue;
import org.springframework.stereotype.Service;

/**
 * Search service with set of operation to perform search operations.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class SearchService {

  public static final int DEFAULT_MAX_SEARCH_RESULT_WINDOW = 10_000;

  private final SearchRepository searchRepository;
  private final SearchFieldProvider searchFieldProvider;
  private final CqlSearchQueryConverter cqlSearchQueryConverter;
  private final ElasticsearchDocumentConverter documentConverter;
  private final SearchQueryConfigurationProperties searchQueryConfiguration;
  private final SearchPreferenceService searchPreferenceService;
  private final Map<Class<?>, SearchResponsePostProcessor<?>> searchResponsePostProcessors;

  /**
   * Prepares search query and executes search request to the search engine.
   *
   * @param request cql search request as {@link CqlSearchRequest} object
   * @return search result.
   */
  public <T> SearchResult<T> search(CqlSearchRequest<T> request) {
    log.debug("search:: by [query: {}, resource: {}]", request.query(), request.resource());
    validateRequest(request);

    var queryBuilder = cqlSearchQueryConverter
      .convertForConsortia(request.query(), request.resource(), request.consortiumConsolidated())
      .from(request.offset())
      .size(request.limit())
      .trackTotalHits(true)
      .fetchSource(getIncludedSourceFields(request), null)
      .timeout(new TimeValue(searchQueryConfiguration.getRequestTimeout().toMillis(), MILLISECONDS));

    var searchResponse = searchRepository.search(request, queryBuilder, buildPreference(request));
    var searchResult = documentConverter.convertToSearchResult(searchResponse, request.resourceClass());

    searchResultPostProcessing(request.resourceClass(), request.includeNumberOfTitles(), searchResult);

    return searchResult;
  }

  private void validateRequest(CqlSearchRequest<?> request) {
    if (request.offset() + request.limit() > DEFAULT_MAX_SEARCH_RESULT_WINDOW) {
      var validationException = new RequestValidationException("The sum of limit and offset should not exceed 10000.",
        "offset + limit", String.valueOf(request.offset() + request.limit()));
      log.warn(validationException.getMessage());
      throw validationException;
    }
  }

  private String[] getIncludedSourceFields(CqlSearchRequest<?> request) {
    return isFalse(request.expandAll())
           ? searchFieldProvider.getSourceFields(request.resource(), request.includeFields())
           : null;
  }

  private String buildPreference(CqlSearchRequest<?> request) {
    var preferenceKey = buildPreferenceKey(request.tenantId(), request.resource().getName(), request.query());
    return searchPreferenceService.getPreferenceForString(preferenceKey);
  }

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
}
