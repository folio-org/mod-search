package org.folio.search.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.model.types.ResourceType.UNKNOWN;
import static org.folio.search.model.types.ResponseGroupType.SEARCH;
import static org.folio.support.TestConstants.RESOURCE_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.searchResult;
import static org.folio.support.utils.TestUtils.searchServiceRequest;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.folio.search.configuration.properties.SearchQueryConfigurationProperties;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.service.setter.SearchResponsePostProcessor;
import org.folio.spring.testing.type.UnitTest;
import org.folio.support.utils.TestUtils.TestResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.index.query.QueryBuilder;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

  private static final String SEARCH_QUERY = "id==" + RESOURCE_ID;
  private static final QueryBuilder ES_TERM_QUERY = termQuery("id", RESOURCE_ID);

  @InjectMocks
  private SearchService searchService;
  @Mock
  private SearchRepository searchRepository;
  @Mock
  private SearchFieldProvider searchFieldProvider;
  @Mock
  private CqlSearchQueryConverter cqlSearchQueryConverter;
  @Mock
  private ElasticsearchDocumentConverter documentConverter;
  @Mock
  private SearchQueryConfigurationProperties searchQueryConfig;
  @Mock
  private SearchResponse searchResponse;
  @Mock
  private SearchPreferenceService searchPreferenceService;
  @Mock
  private Map<Class<?>, SearchResponsePostProcessor<?>> searchResponsePostProcessors = Collections.emptyMap();

  @Test
  void search_positive() {
    var searchRequest = searchServiceRequest(TestResource.class, SEARCH_QUERY);
    var searchSourceBuilder = searchSource().query(ES_TERM_QUERY);
    var expectedSourceBuilder = searchSource().query(ES_TERM_QUERY).size(100).from(0)
      .trackTotalHits(true).fetchSource(array("field1", "field2"), null).timeout(new TimeValue(25000, MILLISECONDS));
    var expectedSearchResult = searchResult(TestResource.of(RESOURCE_ID));

    when(searchFieldProvider.getSourceFields(UNKNOWN, SEARCH)).thenReturn(new String[] {"field1", "field2"});
    when(cqlSearchQueryConverter.convertForConsortia(SEARCH_QUERY, UNKNOWN, false))
      .thenReturn(searchSourceBuilder);
    when(searchRepository.search(eq(searchRequest), eq(expectedSourceBuilder), anyString())).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, TestResource.class))
      .thenReturn(expectedSearchResult);
    when(searchQueryConfig.getRequestTimeout()).thenReturn(Duration.ofSeconds(25));
    when(searchPreferenceService.getPreferenceForString(anyString())).thenReturn("test");

    var actual = searchService.search(searchRequest);
    assertThat(actual).isEqualTo(expectedSearchResult);
  }

  @Test
  void search_negative_sumOfOffsetAndLimitExceeds10000() {
    var searchRequest = CqlSearchRequest.of(TestResource.class, TENANT_ID, SEARCH_QUERY, 500, 9600, false, true, "");
    assertThatThrownBy(() -> searchService.search(searchRequest))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("The sum of limit and offset should not exceed 10000.");
  }

  @Test
  void search_positive_withExpandAll() {
    var searchRequest = searchServiceRequest(TestResource.class, SEARCH_QUERY, true);
    var searchSourceBuilder = searchSource().query(ES_TERM_QUERY);
    var expectedSourceBuilder = searchSource().query(ES_TERM_QUERY).size(100).from(0)
      .trackTotalHits(true).timeout(new TimeValue(1000, MILLISECONDS));
    var expectedSearchResult = searchResult(TestResource.of(RESOURCE_ID));

    when(cqlSearchQueryConverter.convertForConsortia(SEARCH_QUERY, UNKNOWN, false))
      .thenReturn(searchSourceBuilder);
    when(searchRepository.search(eq(searchRequest), eq(expectedSourceBuilder), anyString())).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, TestResource.class))
      .thenReturn(expectedSearchResult);
    when(searchQueryConfig.getRequestTimeout()).thenReturn(Duration.ofSeconds(1));
    when(searchPreferenceService.getPreferenceForString(anyString())).thenReturn("test");

    var actual = searchService.search(searchRequest);
    assertThat(actual).isEqualTo(expectedSearchResult);
  }
}
