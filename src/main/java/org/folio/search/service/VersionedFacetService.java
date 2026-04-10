package org.folio.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.cql.FacetQueryBuilder;
import org.folio.search.cql.flat.FlatSearchQueryConverter;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.model.service.CqlFacetRequest;
import org.folio.search.model.service.QueryResolution;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.consortium.FlatConsortiumSearchHelper;
import org.folio.search.service.converter.ElasticsearchFacetConverter;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.NestedQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

/**
 * Version-aware facet entry point. LEGACY delegates to FacetService.
 * FLAT uses FlatSearchQueryConverter + explicit-index overload.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class VersionedFacetService {

  private final FacetService facetService;
  private final QueryVersionResolver queryVersionResolver;
  private final SearchRepository searchRepository;
  private final FlatSearchQueryConverter flatSearchQueryConverter;
  private final FacetQueryBuilder facetQueryBuilder;
  private final ElasticsearchFacetConverter facetConverter;
  private final FlatConsortiumSearchHelper flatConsortiumSearchHelper;

  public FacetResult getFacets(CqlFacetRequest request, String queryVersion) {
    var resolution = queryVersionResolver.resolve(queryVersion, request.getTenantId());

    if (resolution.pathType() == QueryResolution.PathType.LEGACY) {
      log.debug("getFacets:: delegating to legacy FacetService [version: {}]", queryVersion);
      return facetService.getFacets(request);
    }

    log.debug("getFacets:: using flat facet path [version: {}, alias: {}]",
      queryVersion, resolution.indexName());

    var searchSource = flatSearchQueryConverter.convert(request.getQuery(), request.getResource());
    searchSource.size(0).from(0).fetchSource(false);
    facetQueryBuilder.getFacetAggregations(request, searchSource.query()).forEach(searchSource::aggregation);
    cleanUpFacetSearchSource(searchSource);
    searchSource.query(applySystemScope(searchSource.query(), request.getTenantId()));

    var searchResponse = searchRepository.search(resolution.indexName(), searchSource);
    return facetConverter.convert(searchResponse.getAggregations());
  }

  private QueryBuilder applySystemScope(QueryBuilder query, String tenantId) {
    var scopedQuery = flatConsortiumSearchHelper.addConsortiumFilter(query, tenantId);
    if (scopedQuery instanceof BoolQueryBuilder boolQueryBuilder) {
      boolQueryBuilder.filter(QueryBuilders.termQuery("resourceType", "instance"));
      return boolQueryBuilder;
    }

    return new BoolQueryBuilder()
      .must(scopedQuery)
      .filter(QueryBuilders.termQuery("resourceType", "instance"));
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
