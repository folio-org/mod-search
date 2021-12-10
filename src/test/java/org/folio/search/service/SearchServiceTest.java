package org.folio.search.service;

import static java.util.Collections.emptyMap;
import static org.apache.lucene.search.TotalHits.Relation.EQUAL_TO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.folio.search.utils.TestConstants.EMPTY_OBJECT;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.searchResult;
import static org.folio.search.utils.TestUtils.searchServiceRequest;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchHitConverter;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.TestUtils.TestResource;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

  private static final String SEARCH_QUERY = "id==" + RESOURCE_ID;
  private static final QueryBuilder ES_TERM_QUERY = termQuery("id", RESOURCE_ID);

  @InjectMocks private SearchService searchService;
  @Mock private SearchRepository searchRepository;
  @Mock private SearchFieldProvider searchFieldProvider;
  @Mock private CqlSearchQueryConverter cqlSearchQueryConverter;
  @Mock private ElasticsearchHitConverter elasticsearchHitConverter;

  @Mock private SearchHit searchHit;
  @Mock private SearchHits searchHits;
  @Mock private SearchResponse searchResponse;

  @Test
  void search_positive() {
    var searchRequest = searchServiceRequest(TestResource.class, SEARCH_QUERY);
    var searchSourceBuilder = searchSource().query(ES_TERM_QUERY);
    var expectedSourceBuilder = searchSource().query(ES_TERM_QUERY).size(100).from(0)
      .trackTotalHits(true).fetchSource(array("field1", "field2"), null);

    when(searchFieldProvider.getSourceFields(RESOURCE_NAME)).thenReturn(List.of("field1", "field2"));
    when(cqlSearchQueryConverter.convert(SEARCH_QUERY, RESOURCE_NAME)).thenReturn(searchSourceBuilder);
    when(searchRepository.search(searchRequest, expectedSourceBuilder)).thenReturn(searchResponse);
    mockSearchHit();

    var actual = searchService.search(searchRequest);
    assertThat(actual).isEqualTo(searchResult(new TestResource().id(RESOURCE_ID)));
  }

  @Test
  void search_positive_withExpandAll() {
    var searchRequest = searchServiceRequest(TestResource.class, SEARCH_QUERY, true);
    var searchSourceBuilder = searchSource().query(ES_TERM_QUERY);
    var expectedSourceBuilder = searchSource().query(ES_TERM_QUERY).size(100).from(0).trackTotalHits(true);

    when(cqlSearchQueryConverter.convert(SEARCH_QUERY, RESOURCE_NAME)).thenReturn(searchSourceBuilder);
    when(searchRepository.search(searchRequest, expectedSourceBuilder)).thenReturn(searchResponse);
    mockSearchHit();

    var actual = searchService.search(searchRequest);
    assertThat(actual).isEqualTo(searchResult(new TestResource().id(RESOURCE_ID)));
  }

  @Test
  void search_positive_totalHitsIsNull() {
    var searchRequest = searchServiceRequest(TestResource.class, SEARCH_QUERY, true);
    var searchSourceBuilder = searchSource().query(ES_TERM_QUERY);
    var expectedSourceBuilder = searchSource().query(ES_TERM_QUERY).size(100).from(0).trackTotalHits(true);

    when(cqlSearchQueryConverter.convert(SEARCH_QUERY, RESOURCE_NAME)).thenReturn(searchSourceBuilder);
    when(searchRepository.search(searchRequest, expectedSourceBuilder)).thenReturn(searchResponse);
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(searchHits.getTotalHits()).thenReturn(null);
    when(searchHits.getHits()).thenReturn(new SearchHit[] {});

    var actual = searchService.search(searchRequest);
    assertThat(actual).isEqualTo(searchResult());
  }

  @Test
  void search_positive_nullSearchHits() {
    var searchRequest = searchServiceRequest(TestResource.class, SEARCH_QUERY, true);
    var searchSourceBuilder = searchSource().query(ES_TERM_QUERY);
    var expectedSourceBuilder = searchSource().query(ES_TERM_QUERY).size(100).from(0).trackTotalHits(true);

    when(cqlSearchQueryConverter.convert(SEARCH_QUERY, RESOURCE_NAME)).thenReturn(searchSourceBuilder);
    when(searchRepository.search(searchRequest, expectedSourceBuilder)).thenReturn(searchResponse);
    when(searchResponse.getHits()).thenReturn(null);
    when(searchResponse.toString()).thenReturn(EMPTY_OBJECT);

    assertThatThrownBy(() -> searchService.search(searchRequest))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Failed to parse search response object [response: {}]");
  }

  private void mockSearchHit() {
    when(searchHits.getTotalHits()).thenReturn(new TotalHits(1, EQUAL_TO));
    when(searchHits.getHits()).thenReturn(array(searchHit));
    when(searchHit.getSourceAsMap()).thenReturn(emptyMap());
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(elasticsearchHitConverter.convert(emptyMap(), TestResource.class))
      .thenReturn(new TestResource().id(RESOURCE_ID));
  }
}
