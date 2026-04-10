package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.search.cql.FacetQueryBuilder;
import org.folio.search.cql.flat.FlatSearchQueryConverter;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.model.service.QueryResolution;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.consortium.FlatConsortiumSearchHelper;
import org.folio.search.service.converter.ElasticsearchFacetConverter;
import org.folio.search.utils.TestUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortBuilders;

@UnitTest
@ExtendWith(MockitoExtension.class)
class VersionedFacetServiceTest {

  @Mock
  private FacetService facetService;
  @Mock
  private QueryVersionResolver queryVersionResolver;
  @Mock
  private SearchRepository searchRepository;
  @Mock
  private FlatSearchQueryConverter flatSearchQueryConverter;
  @Mock
  private FacetQueryBuilder facetQueryBuilder;
  @Mock
  private ElasticsearchFacetConverter facetConverter;
  @Mock
  private FlatConsortiumSearchHelper flatConsortiumSearchHelper;
  @Mock
  private SearchResponse searchResponse;

  @InjectMocks
  private VersionedFacetService service;

  @Test
  void getFacets_flatPathAppliesSystemScopeAfterFacetCleanup() {
    var request = TestUtils.facetServiceRequest("member-tenant", ResourceType.INSTANCE, "title all test", "source:5");
    var searchSource = new SearchSourceBuilder()
      .query(QueryBuilders.boolQuery().filter(QueryBuilders.termQuery("language", "eng")))
      .sort(SortBuilders.fieldSort("title"));
    var scopedQuery = QueryBuilders.boolQuery()
      .must(QueryBuilders.boolQuery())
      .filter(QueryBuilders.boolQuery()
        .should(QueryBuilders.termQuery("tenantId", "member-tenant"))
        .should(QueryBuilders.termQuery("shared", true))
        .minimumShouldMatch(1));
    var expectedResult = new FacetResult();

    when(queryVersionResolver.resolve(null, "member-tenant"))
      .thenReturn(new QueryResolution("member-alias", QueryResolution.PathType.FLAT));
    when(flatSearchQueryConverter.convert("title all test", ResourceType.INSTANCE)).thenReturn(searchSource);
    when(facetQueryBuilder.getFacetAggregations(eq(request), any())).thenReturn(List.of());
    when(flatConsortiumSearchHelper.addConsortiumFilter(any(), eq("member-tenant"))).thenReturn(scopedQuery);
    when(searchRepository.search(eq("member-alias"), any(SearchSourceBuilder.class))).thenReturn(searchResponse);
    when(facetConverter.convert(searchResponse.getAggregations())).thenReturn(expectedResult);

    var result = service.getFacets(request, null);

    assertThat(result).isSameAs(expectedResult);
    verify(searchRepository).search(eq("member-alias"), argThat(source -> {
      assertThat(source.sorts()).isEmpty();
      assertThat(source.query()).isInstanceOf(BoolQueryBuilder.class);
      var query = (BoolQueryBuilder) source.query();
      assertThat(query.filter()).hasSize(2);
      assertThat(query.filter().stream().anyMatch(filter ->
        filter.toString().contains("resourceType") && filter.toString().contains("instance"))).isTrue();
      assertThat(query.filter().stream().anyMatch(filter ->
        filter.toString().contains("tenantId") && filter.toString().contains("shared"))).isTrue();
      assertThat(query.toString()).doesNotContain("language");
      return true;
    }));
  }

  @Test
  void getFacets_flatPathDoesNotPreserveExplicitTenantOrSharedFilters() {
    var request = TestUtils.facetServiceRequest("member-tenant", ResourceType.INSTANCE, "tenantId==foo", "source:5");
    var searchSource = new SearchSourceBuilder()
      .query(QueryBuilders.boolQuery()
        .filter(QueryBuilders.termQuery("tenantId", "foo"))
        .filter(QueryBuilders.termQuery("shared", true)));
    var scopedQuery = QueryBuilders.boolQuery()
      .must(QueryBuilders.boolQuery())
      .filter(QueryBuilders.boolQuery()
        .should(QueryBuilders.termQuery("tenantId", "member-tenant"))
        .should(QueryBuilders.termQuery("shared", true))
        .minimumShouldMatch(1));
    var expectedResult = new FacetResult();

    when(queryVersionResolver.resolve(null, "member-tenant"))
      .thenReturn(new QueryResolution("member-alias", QueryResolution.PathType.FLAT));
    when(flatSearchQueryConverter.convert("tenantId==foo", ResourceType.INSTANCE)).thenReturn(searchSource);
    when(facetQueryBuilder.getFacetAggregations(eq(request), any())).thenReturn(List.of());
    when(flatConsortiumSearchHelper.addConsortiumFilter(any(), eq("member-tenant"))).thenReturn(scopedQuery);
    when(searchRepository.search(eq("member-alias"), any(SearchSourceBuilder.class))).thenReturn(searchResponse);
    when(facetConverter.convert(searchResponse.getAggregations())).thenReturn(expectedResult);

    var result = service.getFacets(request, null);

    assertThat(result).isSameAs(expectedResult);
    verify(searchRepository).search(eq("member-alias"), argThat(source -> {
      var queryText = source.query().toString();
      assertThat(queryText).contains("member-tenant");
      assertThat(queryText).contains("resourceType");
      assertThat(queryText).doesNotContain("\"foo\"");
      return true;
    }));
  }
}
