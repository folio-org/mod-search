package org.folio.search.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.repository.ConsortiumLibraryRepository.LIBRARY_INDEX;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.io.IOException;
import org.folio.search.domain.dto.ConsortiumLibrary;
import org.folio.search.model.SearchResult;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.search.sort.FieldSortBuilder;
import org.opensearch.search.sort.SortOrder;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ConsortiumLibraryRepositoryTest {

  @Mock
  private IndexNameProvider indexNameProvider;
  @Mock
  private ElasticsearchDocumentConverter documentConverter;
  @Mock
  private RestHighLevelClient client;
  @InjectMocks
  private ConsortiumLibraryRepository repository;

  @Captor
  private ArgumentCaptor<SearchRequest> requestCaptor;

  @BeforeEach
  void setUp() {
    lenient().when(indexNameProvider.getIndexName(LIBRARY_INDEX, TENANT_ID)).thenReturn(INDEX_NAME);
  }

  @Test
  void fetchLibraries_positive() throws IOException {
    var limit = 123;
    var offset = 321;
    var sortBy = "test";
    var searchResponse = mock(SearchResponse.class);
    var searchResult = Mockito.<SearchResult<ConsortiumLibrary>>mock();

    when(client.search(requestCaptor.capture(), eq(DEFAULT))).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, ConsortiumLibrary.class)).thenReturn(searchResult);

    var actual = repository.fetchLibraries(TENANT_ID, null, limit, offset, sortBy, null);

    assertThat(actual).isEqualTo(searchResult);

    assertThat(requestCaptor.getValue())
      .matches(request -> request.indices().length == 1 && request.indices()[0].equals(INDEX_NAME))
      .satisfies(request -> {
        var source = request.source();
        assertThat(source.size()).isEqualTo(limit);
        assertThat(source.from()).isEqualTo(offset);
        assertThat(source.sorts()).hasSize(1);
        assertThat(source.sorts().get(0)).isInstanceOf(FieldSortBuilder.class);

        var sort = (FieldSortBuilder) source.sorts().get(0);
        assertThat(sort.getFieldName()).isEqualTo(sortBy);
        assertThat(sort.order()).isEqualTo(SortOrder.ASC);

        assertThat(source.query()).isNull();
      });
  }

  @Test
  void fetchLibraries_positive_withTenantFilter() throws IOException {
    var limit = 123;
    var offset = 321;
    var sortBy = "test";
    var searchResponse = mock(SearchResponse.class);
    var searchResult = Mockito.<SearchResult<ConsortiumLibrary>>mock();

    when(client.search(requestCaptor.capture(), eq(DEFAULT))).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, ConsortiumLibrary.class)).thenReturn(searchResult);

    var actual = repository.fetchLibraries(TENANT_ID, MEMBER_TENANT_ID, limit, offset, sortBy, null);

    assertThat(actual).isEqualTo(searchResult);

    assertThat(requestCaptor.getValue())
      .matches(request -> request.indices().length == 1 && request.indices()[0].equals(INDEX_NAME))
      .satisfies(request -> {
        var source = request.source();
        assertThat(source.size()).isEqualTo(limit);
        assertThat(source.from()).isEqualTo(offset);
        assertThat(source.sorts()).hasSize(1);
        assertThat(source.sorts().get(0)).isInstanceOf(FieldSortBuilder.class);

        var sort = (FieldSortBuilder) source.sorts().get(0);
        assertThat(sort.getFieldName()).isEqualTo(sortBy);
        assertThat(sort.order()).isEqualTo(SortOrder.ASC);
        assertThat(source.query()).isInstanceOf(TermQueryBuilder.class);

        var query = (TermQueryBuilder) source.query();
        assertThat(query.fieldName()).isEqualTo("tenantId");
        assertThat(query.value()).isEqualTo(MEMBER_TENANT_ID);
      });
  }

  @Test
  void fetchLibraries_positive_sortDesc() throws IOException {
    var limit = 123;
    var offset = 321;
    var sortBy = "test";
    var searchResponse = mock(SearchResponse.class);
    var searchResult = Mockito.<SearchResult<ConsortiumLibrary>>mock();

    when(client.search(requestCaptor.capture(), eq(DEFAULT))).thenReturn(searchResponse);
    when(documentConverter.convertToSearchResult(searchResponse, ConsortiumLibrary.class)).thenReturn(searchResult);

    var actual = repository.fetchLibraries(TENANT_ID, null, limit, offset, sortBy,
      org.folio.search.domain.dto.SortOrder.DESC);

    assertThat(actual).isEqualTo(searchResult);

    assertThat(requestCaptor.getValue())
      .matches(request -> request.indices().length == 1 && request.indices()[0].equals(INDEX_NAME))
      .satisfies(request -> {
        var source = request.source();
        assertThat(source.size()).isEqualTo(limit);
        assertThat(source.from()).isEqualTo(offset);
        assertThat(source.sorts()).hasSize(1);
        assertThat(source.sorts().get(0)).isInstanceOf(FieldSortBuilder.class);

        var sort = (FieldSortBuilder) source.sorts().get(0);
        assertThat(sort.getFieldName()).isEqualTo(sortBy);
        assertThat(sort.order()).isEqualTo(SortOrder.DESC);
        assertThat(source.query()).isNull();
      });
  }

}
