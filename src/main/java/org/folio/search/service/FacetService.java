package org.folio.search.service;

import java.util.List;
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
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class FacetService {

  private static final String ITEMS_EFFECTIVE_LOCATION_ID = "items.effectiveLocationId";
  private static final String TENANT_ID = "holdings.tenantId";
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
    cleanUpFacetSearchSource(searchSource, List.of(ITEMS_EFFECTIVE_LOCATION_ID, TENANT_ID));

    var searchResponse = searchRepository.search(request, searchSource);
    return facetConverter.convert(searchResponse.getAggregations());
  }

  private static void cleanUpFacetSearchSource(SearchSourceBuilder searchSource, List<String> filterNamesToKeep) {
    var query = searchSource.query();
    if (query instanceof BoolQueryBuilder boolQuery) {
      boolQuery.filter().removeIf(filter -> !(filter instanceof TermQueryBuilder termFilter
        && filterNamesToKeep.contains(termFilter.fieldName())));
    }

    if (CollectionUtils.isNotEmpty(searchSource.sorts())) {
      searchSource.sorts().clear();
    }
  }
}
