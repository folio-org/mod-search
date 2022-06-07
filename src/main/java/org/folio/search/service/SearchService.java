package org.folio.search.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.folio.search.model.types.ResponseGroupType.SEARCH;

import lombok.RequiredArgsConstructor;
import org.opensearch.common.unit.TimeValue;
import org.folio.search.configuration.properties.SearchQueryConfigurationProperties;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.springframework.stereotype.Service;

/**
 * Search service with set of operation to perform search operations.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

  private final SearchRepository searchRepository;
  private final SearchFieldProvider searchFieldProvider;
  private final CqlSearchQueryConverter cqlSearchQueryConverter;
  private final ElasticsearchDocumentConverter documentConverter;
  private final SearchQueryConfigurationProperties searchQueryConfiguration;

  /**
   * Prepares search query and executes search request to the search engine.
   *
   * @param request cql search request as {@link CqlSearchRequest} object
   * @return search result.
   */
  public <T> SearchResult<T> search(CqlSearchRequest<T> request) {
    if (request.getOffset() + request.getLimit() > 10_000L) {
      throw new RequestValidationException("The sum of limit and offset should not exceed 10000.",
        "offset + limit", String.valueOf(request.getOffset() + request.getLimit()));
    }
    var resource = request.getResource();
    var requestTimeout = searchQueryConfiguration.getRequestTimeout();
    var queryBuilder = cqlSearchQueryConverter.convert(request.getQuery(), resource)
      .from(request.getOffset())
      .size(request.getLimit())
      .trackTotalHits(true)
      .timeout(new TimeValue(requestTimeout.toMillis(), MILLISECONDS));

    if (isFalse(request.getExpandAll())) {
      var includes = searchFieldProvider.getSourceFields(resource, SEARCH);
      queryBuilder.fetchSource(includes, null);
    }

    var searchResponse = searchRepository.search(request, queryBuilder);
    return documentConverter.convertToSearchResult(searchResponse, request.getResourceClass());
  }
}
