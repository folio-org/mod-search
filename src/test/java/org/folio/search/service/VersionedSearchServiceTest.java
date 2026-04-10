package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.TotalHits.Relation;
import org.folio.search.configuration.properties.SearchQueryConfigurationProperties;
import org.folio.search.cql.flat.FlatSearchQueryConverter;
import org.folio.search.domain.dto.Instance;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.QueryResolution;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.consortium.FlatConsortiumSearchHelper;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.search.utils.TestUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;

@UnitTest
@ExtendWith(MockitoExtension.class)
class VersionedSearchServiceTest {

  @Mock
  private SearchService searchService;
  @Mock
  private QueryVersionResolver queryVersionResolver;
  @Mock
  private SearchRepository searchRepository;
  @Mock
  private FlatSearchQueryConverter flatSearchQueryConverter;
  @Mock
  private SearchPreferenceService searchPreferenceService;
  @Mock
  private ConsortiumTenantProvider consortiumTenantProvider;

  private VersionedSearchService service;

  @BeforeEach
  void setUp() {
    service = new VersionedSearchService(
      searchService,
      queryVersionResolver,
      searchRepository,
      new ElasticsearchDocumentConverter(new ObjectMapper()),
      flatSearchQueryConverter,
      SearchQueryConfigurationProperties.of(Duration.ofSeconds(5), 3d, true, 10_000),
      searchPreferenceService,
      new FlatConsortiumSearchHelper(consortiumTenantProvider),
      Map.of());
  }

  @Test
  void search_flatPathAppliesConsortiumScopeWhenHydratingChildren() {
    var request = TestUtils.searchServiceRequest(Instance.class, "member-tenant", "title all test", true, 10);
    var primarySource = new SearchSourceBuilder().query(QueryBuilders.matchAllQuery());
    var primaryResponse = mock(SearchResponse.class);
    var primaryHits = mock(SearchHits.class);
    var instanceHit = hit(Map.of(
      "id", "instance-1",
      "resourceType", "instance",
      "instance", Map.of("title", "Instance title")));

    when(queryVersionResolver.resolve(null, "member-tenant"))
      .thenReturn(new QueryResolution("member-alias", QueryResolution.PathType.FLAT));
    when(flatSearchQueryConverter.convert("title all test", request.getResource())).thenReturn(primarySource);
    when(consortiumTenantProvider.isConsortiumTenant("member-tenant")).thenReturn(true);
    when(consortiumTenantProvider.isMemberTenant("member-tenant")).thenReturn(true);
    when(searchPreferenceService.getPreferenceForString(any())).thenReturn("preference");
    when(searchRepository.search(eq("member-alias"), any(SearchSourceBuilder.class), eq("preference")))
      .thenReturn(primaryResponse);
    when(primaryResponse.getHits()).thenReturn(primaryHits);
    when(primaryHits.getHits()).thenReturn(new SearchHit[]{instanceHit});
    when(primaryHits.getTotalHits()).thenReturn(new TotalHits(1, Relation.EQUAL_TO));

    doAnswer(invocation -> {
      var childQuery = ((SearchSourceBuilder) invocation.getArgument(1)).query().toString();
      assertThat(childQuery).contains("resourceType");
      assertThat(childQuery).contains("tenantId");
      assertThat(childQuery).contains("member-tenant");
      assertThat(childQuery).contains("shared");

      @SuppressWarnings("unchecked")
      var consumer = (java.util.function.Consumer<SearchHit[]>) invocation.getArgument(2);
      if (childQuery.contains("\"item\"")) {
        consumer.accept(new SearchHit[]{hit(Map.of(
          "id", "item-1",
          "instanceId", "instance-1",
          "resourceType", "item",
          "item", Map.of(
            "holdingsRecordId", "holding-1",
            "effectiveCallNumberComponents", Map.of("callNumber", "QA1"))))});
      } else {
        consumer.accept(new SearchHit[0]);
      }
      return null;
    }).when(searchRepository).streamDocuments(eq("member-alias"), any(SearchSourceBuilder.class), any());

    SearchResult<Instance> result = service.search(request, null);

    assertThat(result.getTotalRecords()).isEqualTo(1);
    assertThat(result.getRecords()).hasSize(1);
    assertThat(result.getRecords().getFirst().getItems())
      .extracting(org.folio.search.domain.dto.Item::getId)
      .containsExactly("item-1");
    verify(searchRepository, times(2))
      .streamDocuments(eq("member-alias"), any(SearchSourceBuilder.class), any());
  }

  @Test
  void search_flatPathDefaultSkipsChildHydration() {
    var request = TestUtils.searchServiceRequest(Instance.class, "member-tenant", "title all test", false, 10);
    var primarySource = new SearchSourceBuilder().query(QueryBuilders.matchAllQuery());
    var primaryResponse = mock(SearchResponse.class);
    var primaryHits = mock(SearchHits.class);
    var instanceHit = hit(Map.of(
      "id", "instance-1",
      "resourceType", "instance",
      "instance", Map.of("title", "Instance title")));

    when(queryVersionResolver.resolve(null, "member-tenant"))
      .thenReturn(new QueryResolution("member-alias", QueryResolution.PathType.FLAT));
    when(flatSearchQueryConverter.convert("title all test", request.getResource())).thenReturn(primarySource);
    when(consortiumTenantProvider.isConsortiumTenant("member-tenant")).thenReturn(true);
    when(consortiumTenantProvider.isMemberTenant("member-tenant")).thenReturn(true);
    when(searchPreferenceService.getPreferenceForString(any())).thenReturn("preference");
    when(searchRepository.search(eq("member-alias"), any(SearchSourceBuilder.class), eq("preference")))
      .thenReturn(primaryResponse);
    when(primaryResponse.getHits()).thenReturn(primaryHits);
    when(primaryHits.getHits()).thenReturn(new SearchHit[]{instanceHit});
    when(primaryHits.getTotalHits()).thenReturn(new TotalHits(1, Relation.EQUAL_TO));

    SearchResult<Instance> result = service.search(request, null);

    assertThat(result.getTotalRecords()).isEqualTo(1);
    assertThat(result.getRecords()).hasSize(1);
    assertThat(result.getRecords().getFirst().getItems()).isNullOrEmpty();
    assertThat(result.getRecords().getFirst().getHoldings()).isNullOrEmpty();
    verify(searchRepository, never()).streamDocuments(any(), any(SearchSourceBuilder.class), any());
  }

  private static SearchHit hit(Map<String, Object> source) {
    var hit = mock(SearchHit.class);
    when(hit.getSourceAsMap()).thenReturn(source);
    return hit;
  }
}
