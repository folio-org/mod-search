package org.folio.search.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.client.RequestOptions.DEFAULT;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.searchServiceRequest;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.SearchResult;
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
  @Mock private RestHighLevelClient elasticsearchClient;
  @Mock private SearchResponse searchResponse;

  @Test
  void search_positive() throws IOException {
    var totalResults = 20;
    var queryBuilder = SearchSourceBuilder.searchSource();
    var esSearchRequest = new SearchRequest().indices(INDEX_NAME).routing(TENANT_ID).source(queryBuilder);

    when(elasticsearchClient.search(esSearchRequest, DEFAULT)).thenReturn(searchResponse);

    var expectedResult = new SearchResult();
    expectedResult.setTotalRecords(totalResults);
    expectedResult.setInstances(List.of(new Instance()));

    var searchRequest = searchServiceRequest(RESOURCE_NAME, "query");
    var actual = searchRepository.search(searchRequest, queryBuilder);
    assertThat(actual).isEqualTo(searchResponse);
  }
}
