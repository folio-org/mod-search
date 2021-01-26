package org.folio.search.repository;

import static java.util.Collections.emptyMap;
import static org.apache.lucene.search.TotalHits.Relation.EQUAL_TO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.client.RequestOptions.DEFAULT;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.array;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.service.converter.ElasticsearchHitConverter;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchRepositoryTest {

  @InjectMocks private SearchRepository searchRepository;
  @Mock private ElasticsearchHitConverter elasticsearchHitConverter;
  @Mock private RestHighLevelClient elasticsearchClient;
  @Mock private SearchResponse searchResponse;
  @Mock private SearchHits searchHits;
  @Mock private SearchHit searchHit;

  @Test
  void search_positive() throws IOException {
    var totalResults = 20;
    var queryBuilder = SearchSourceBuilder.searchSource();
    var esSearchRequest = new SearchRequest().indices(INDEX_NAME).routing(TENANT_ID).source(queryBuilder);

    when(searchHits.getTotalHits()).thenReturn(new TotalHits(totalResults, EQUAL_TO));
    when(searchHits.getHits()).thenReturn(array(searchHit));
    when(searchHit.getSourceAsMap()).thenReturn(emptyMap());
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(elasticsearchHitConverter.convert(emptyMap(), Instance.class)).thenReturn(new Instance());
    when(elasticsearchClient.search(esSearchRequest, DEFAULT)).thenReturn(searchResponse);

    var expectedResult = new SearchResult();
    expectedResult.setTotalRecords(totalResults);
    expectedResult.setInstances(List.of(new Instance()));

    var searchRequest = CqlSearchRequest.of(RESOURCE_NAME, "query", TENANT_ID, 1, 20);
    var actual = searchRepository.search(searchRequest, queryBuilder);
    assertThat(actual).isEqualTo(expectedResult);
  }
}
