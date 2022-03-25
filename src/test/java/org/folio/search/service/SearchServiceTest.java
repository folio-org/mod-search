package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.searchResult;
import static org.folio.search.utils.TestUtils.searchServiceRequest;
import static org.mockito.Mockito.when;

import java.util.List;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
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
  @Mock private ElasticsearchDocumentConverter documentConverter;
  @Mock private SearchResponse searchResponse;

  @Test
  void search_positive() {
    var searchRequest = searchServiceRequest(TestResource.class, SEARCH_QUERY);
    var searchSourceBuilder = searchSource().query(ES_TERM_QUERY);
    var expectedSourceBuilder = searchSource().query(ES_TERM_QUERY).size(100).from(0)
      .trackTotalHits(true).fetchSource(array("field1", "field2"), null);
    var expectedSearchResult = searchResult(TestResource.of(RESOURCE_ID));

    when(searchFieldProvider.getSourceFields(RESOURCE_NAME)).thenReturn(List.of("field1", "field2"));
    when(cqlSearchQueryConverter.convert(SEARCH_QUERY, RESOURCE_NAME)).thenReturn(searchSourceBuilder);
    when(searchRepository.search(searchRequest, expectedSourceBuilder)).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, TestResource.class)).thenReturn(expectedSearchResult);

    var actual = searchService.search(searchRequest);
    assertThat(actual).isEqualTo(expectedSearchResult);
  }

  @Test
  void search_negative_sumOfOffsetAndLimitExceeds10000() {
    var searchRequest = CqlSearchRequest
      .of(TestResource.class, TENANT_ID, SEARCH_QUERY, 500, 9600, false);
    assertThatThrownBy(() -> searchService.search(searchRequest))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("The sum of limit and offset should not exceed 10000.");
  }

  @Test
  void search_positive_withExpandAll() {
    var searchRequest = searchServiceRequest(TestResource.class, SEARCH_QUERY, true);
    var searchSourceBuilder = searchSource().query(ES_TERM_QUERY);
    var expectedSourceBuilder = searchSource().query(ES_TERM_QUERY).size(100).from(0).trackTotalHits(true);
    var expectedSearchResult = searchResult(TestResource.of(RESOURCE_ID));

    when(cqlSearchQueryConverter.convert(SEARCH_QUERY, RESOURCE_NAME)).thenReturn(searchSourceBuilder);
    when(searchRepository.search(searchRequest, expectedSourceBuilder)).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, TestResource.class)).thenReturn(expectedSearchResult);

    var actual = searchService.search(searchRequest);
    assertThat(actual).isEqualTo(expectedSearchResult);
  }
}
