package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.model.rest.response.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.repository.SearchRepository;
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
  @Mock private CqlSearchQueryConverter cqlSearchQueryConverter;

  @Test
  void search_positive() {
    var expectedResult = SearchResult.of(0, Collections.emptyList());
    var searchRequest = CqlSearchRequest.of(RESOURCE_NAME, "query", TENANT_ID, 0, 10);
    var searchSourceBuilder = searchSource();

    when(searchRepository.search(searchRequest, searchSourceBuilder)).thenReturn(expectedResult);
    when(cqlSearchQueryConverter.convert(searchRequest)).thenReturn(searchSourceBuilder);

    var actual = searchService.search(searchRequest);
    assertThat(actual).isEqualTo(expectedResult);
  }
}
