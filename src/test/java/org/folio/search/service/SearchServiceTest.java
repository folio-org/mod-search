package org.folio.search.service;

import static java.util.Collections.emptyMap;
import static org.apache.lucene.search.TotalHits.Relation.EQUAL_TO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.searchResult;
import static org.folio.search.utils.TestUtils.searchServiceRequest;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.folio.search.cql.CqlSearchQueryConverter;
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
    var recordId = randomId();
    var query = "id==" + recordId;
    var searchRequest = searchServiceRequest(TestResource.class, query);
    var termQuery = termQuery("id", recordId);
    var searchSourceBuilder = searchSource().query(termQuery);
    var expectedSourceBuilder = searchSource().query(termQuery).size(100).from(0)
      .trackTotalHits(true).fetchSource(array("field1", "field2"), null);

    when(searchFieldProvider.getSourceFields(RESOURCE_NAME)).thenReturn(List.of("field1", "field2"));
    when(cqlSearchQueryConverter.convert(query, RESOURCE_NAME)).thenReturn(searchSourceBuilder);
    when(searchRepository.search(searchRequest, expectedSourceBuilder)).thenReturn(searchResponse);
    mockSearchHit(recordId);

    var actual = searchService.search(searchRequest);
    assertThat(actual).isEqualTo(searchResult(new TestResource().id(recordId)));
  }

  @Test
  void search_positive_withExpandAll() {
    var instanceId = randomId();
    var query = "id==" + instanceId;
    var searchRequest = searchServiceRequest(TestResource.class, query, true);
    var termQuery = termQuery("id", instanceId);
    var searchSourceBuilder = searchSource().query(termQuery);
    var expectedSourceBuilder = searchSource().query(termQuery).size(100).from(0).trackTotalHits(true);

    when(cqlSearchQueryConverter.convert(query, RESOURCE_NAME)).thenReturn(searchSourceBuilder);
    when(searchRepository.search(searchRequest, expectedSourceBuilder)).thenReturn(searchResponse);
    mockSearchHit(instanceId);

    var actual = searchService.search(searchRequest);
    assertThat(actual).isEqualTo(searchResult(new TestResource().id(instanceId)));
  }

  private void mockSearchHit(String instanceId) {
    when(searchHits.getTotalHits()).thenReturn(new TotalHits(1, EQUAL_TO));
    when(searchHits.getHits()).thenReturn(array(searchHit));
    when(searchHit.getSourceAsMap()).thenReturn(emptyMap());
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(elasticsearchHitConverter.convert(emptyMap(), TestResource.class))
      .thenReturn(new TestResource().id(instanceId));
  }
}
