package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.defaultFacetServiceRequest;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;

import java.util.List;
import java.util.UUID;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.cql.FacetQueryBuilder;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.model.service.CqlFacetRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchFacetConverter;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.Aggregations;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FacetServiceTest {

  private static final String QUERY = "test-query";

  @InjectMocks
  private FacetService facetService;
  @Mock
  private CqlSearchQueryConverter cqlSearchQueryConverter;
  @Mock
  private ElasticsearchFacetConverter facetConverter;
  @Mock
  private FacetQueryBuilder facetQueryBuilder;
  @Mock
  private SearchRepository searchRepository;
  @Mock
  private SearchResponse searchResponse;
  @Mock
  private Aggregations aggregations;

  @Test
  void getFacets_positive() {
    var matchQuery = matchQuery("title", "value");
    var boolQuery = boolQuery().must(matchQuery).filter(termQuery("filter", "value"));
    var request = facetRequest("source");
    var sourceAggregation = AggregationBuilders.terms("source").field("source").size(Integer.MAX_VALUE);
    var searchSource = searchSource().size(0).from(0).fetchSource(false).aggregation(sourceAggregation)
      .query(boolQuery().must(matchQuery));

    when(cqlSearchQueryConverter.convertForConsortia(QUERY, RESOURCE_NAME)).thenReturn(searchSource().query(boolQuery));
    when(facetQueryBuilder.getFacetAggregations(request, boolQuery)).thenReturn(List.of(sourceAggregation));
    when(searchRepository.search(request, searchSource)).thenReturn(searchResponse);
    when(searchResponse.getAggregations()).thenReturn(aggregations);
    when(facetConverter.convert(aggregations)).thenReturn(new FacetResult());

    var actual = facetService.getFacets(request);
    assertThat(actual).isEqualTo(new FacetResult());
  }

  @Test
  void getFacets_positive_keepGivenFilters() {
    var matchQuery = matchQuery("title", "value");
    var filterToKeep1 = "items.effectiveLocationId";
    var filterToKeep2 = "holdings.tenantId";
    var tenant = "college";
    var uuid = UUID.randomUUID();
    String query = "%s==(\"%s\") and %s==(\"%s\")".formatted(filterToKeep1, uuid, filterToKeep2, tenant);
    var boolQuery = boolQuery().must(matchQuery).filter(termQuery(filterToKeep1, uuid.toString()))
      .must(matchQuery).filter(termQuery(filterToKeep1, uuid.toString()));
    var request = defaultFacetServiceRequest(RESOURCE_NAME, query, "holdings.tenantId:6");
    var sourceAggregation = AggregationBuilders.terms("source").field("source").size(Integer.MAX_VALUE);
    var searchSource = searchSource().size(0).from(0).fetchSource(false).aggregation(sourceAggregation)
      .query(boolQuery);

    when(cqlSearchQueryConverter.convertForConsortia(query, RESOURCE_NAME)).thenReturn(searchSource().query(boolQuery));
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

    when(cqlSearchQueryConverter.convertForConsortia(QUERY, RESOURCE_NAME)).thenReturn(cqlQuerySearchSource);
    when(facetQueryBuilder.getFacetAggregations(request, query)).thenReturn(List.of(sourceAgg));
    when(searchRepository.search(request, searchSource)).thenReturn(searchResponse);
    when(searchResponse.getAggregations()).thenReturn(aggregations);
    when(facetConverter.convert(aggregations)).thenReturn(new FacetResult());

    var actual = facetService.getFacets(request);
    assertThat(actual).isEqualTo(new FacetResult());
  }

  private static CqlFacetRequest facetRequest(String... facetNames) {
    return defaultFacetServiceRequest(RESOURCE_NAME, QUERY, facetNames);
  }
}
