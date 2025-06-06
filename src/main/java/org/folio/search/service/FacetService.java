package org.folio.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.cql.FacetQueryBuilder;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.model.service.CqlFacetRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchFacetConverter;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.NestedQueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class FacetService {

  private final SearchRepository searchRepository;
  private final CqlSearchQueryConverter cqlSearchQueryConverter;
  private final FacetQueryBuilder facetQueryBuilder;
  private final ElasticsearchFacetConverter facetConverter;

  /**
   * Prepares facet search query and executes facet request to the search engine.
   *
   * @param request cql search request as {@link CqlFacetRequest} object
   * @return facet result with found facets for given facet request.
   */
  public FacetResult getFacets(CqlFacetRequest request) {
    log.debug("getFacets:: by [query: {}, resource: {}]", request.getQuery(), request.getResource());
    var searchSource = cqlSearchQueryConverter.convertForConsortia(request.getQuery(), request.getResource());
    searchSource.size(0).from(0).fetchSource(false);

    facetQueryBuilder.getFacetAggregations(request, searchSource.query()).forEach(searchSource::aggregation);
    cleanUpFacetSearchSource(searchSource);

    var searchResponse = searchRepository.search(request, searchSource);
    return facetConverter.convert(searchResponse.getAggregations());
  }

  private static void cleanUpFacetSearchSource(SearchSourceBuilder searchSource) {
    var query = searchSource.query();
    if (query instanceof BoolQueryBuilder boolQuery) {
      boolQuery.filter().clear();
      for (var queryBuilder : boolQuery.must()) {
        if (queryBuilder instanceof NestedQueryBuilder nestedQueryBuilder
            && nestedQueryBuilder.query() instanceof BoolQueryBuilder nestedBoolQuery) {
          nestedBoolQuery.filter().clear();
        }
      }
    }

    if (CollectionUtils.isNotEmpty(searchSource.sorts())) {
      searchSource.sorts().clear();
    }
  }
}
