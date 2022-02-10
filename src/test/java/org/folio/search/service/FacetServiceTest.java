package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.facetServiceRequest;
import static org.mockito.Mockito.when;

import java.util.List;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.cql.FacetQueryBuilder;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.model.service.CqlFacetRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchFacetConverter;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FacetServiceTest {

  private static final String QUERY = "test-query";

  @InjectMocks private FacetService facetService;
  @Mock private CqlSearchQueryConverter cqlSearchQueryConverter;
  @Mock private ElasticsearchFacetConverter facetConverter;
  @Mock private FacetQueryBuilder facetQueryBuilder;
  @Mock private SearchRepository searchRepository;
  @Mock private SearchResponse searchResponse;
  @Mock private Aggregations aggregations;

  @Test
  void getFacets_positive() {
    var matchQuery = matchQuery("title", "value");
    var boolQuery = boolQuery().must(matchQuery).filter(termQuery("filter", "value"));
    var request = facetRequest("source");
    var sourceAggregation = AggregationBuilders.terms("source").field("source").size(Integer.MAX_VALUE);
    var searchSource = searchSource().size(0).from(0).fetchSource(false).aggregation(sourceAggregation)
      .query(boolQuery().must(matchQuery));

    when(cqlSearchQueryConverter.convert(QUERY, RESOURCE_NAME)).thenReturn(searchSource().query(boolQuery));
    when(facetQueryBuilder.getFacetAggregations(request, boolQuery)).thenReturn(List.of(sourceAggregation));
    when(searchRepository.search(request, searchSource)).thenReturn(searchResponse);
    when(searchResponse.getAggregations()).thenReturn(aggregations);
    when(facetConverter.convert(aggregations)).thenReturn(new FacetResult());

    var actual = facetService.getFacets(request);
    assertThat(actual).isEqualTo(new FacetResult());
  }

  @Test
  void getFacets_positive_queryWithoutFilters() {
    var query = matchQuery("title", "value");
    var request = facetRequest("source");
    var sourceAgg = AggregationBuilders.terms("source").field("source").size(Integer.MAX_VALUE);
    var searchSource = searchSource().size(0).from(0).fetchSource(false).aggregation(sourceAgg).query(query).sort("1");
    searchSource.sorts().clear();
    var cqlQuerySearchSource = searchSource().query(query).sort("title_sort");

    when(cqlSearchQueryConverter.convert(QUERY, RESOURCE_NAME)).thenReturn(cqlQuerySearchSource);
    when(facetQueryBuilder.getFacetAggregations(request, query)).thenReturn(List.of(sourceAgg));
    when(searchRepository.search(request, searchSource)).thenReturn(searchResponse);
    when(searchResponse.getAggregations()).thenReturn(aggregations);
    when(facetConverter.convert(aggregations)).thenReturn(new FacetResult());

    var actual = facetService.getFacets(request);
    assertThat(actual).isEqualTo(new FacetResult());
  }

  private static CqlFacetRequest facetRequest(String... facetNames) {
    return facetServiceRequest(RESOURCE_NAME, QUERY, facetNames);
  }
}
