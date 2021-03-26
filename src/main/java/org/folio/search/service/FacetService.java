package org.folio.search.service;

import static org.folio.search.utils.SearchQueryUtils.isBoolQuery;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.model.service.CqlFacetServiceRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchFacetConverter;
import org.folio.search.service.converter.FacetQueryBuilder;
import org.springframework.stereotype.Service;

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
   * @param request cql search request as {@link CqlFacetServiceRequest} object
   * @return facet result with found facets for given facet request.
   */
  public FacetResult getFacets(CqlFacetServiceRequest request) {
    var searchSource = cqlSearchQueryConverter.convert(request.getQuery(), request.getResource());
    searchSource.size(0).from(0).fetchSource(false);

    facetQueryBuilder.getFacetAggregations(request, searchSource.query()).forEach(searchSource::aggregation);
    cleanUpFacetSearchSource(searchSource);

    var searchResponse = searchRepository.search(request, searchSource);
    return facetConverter.convert(searchResponse.getAggregations());
  }

  private static void cleanUpFacetSearchSource(SearchSourceBuilder searchSource) {
    var query = searchSource.query();
    if (isBoolQuery(query)) {
      ((BoolQueryBuilder) query).filter().clear();
    }
    if (CollectionUtils.isNotEmpty(searchSource.sorts())) {
      searchSource.sorts().clear();
    }
  }
}
