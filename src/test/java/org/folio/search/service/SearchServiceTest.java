package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import org.folio.search.model.rest.response.SearchResult;
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

  @Test
  void search_positive() {
    var expectedResult = new SearchResult();
    when(searchRepository.search(matchAllQuery(), TENANT_ID)).thenReturn(expectedResult);
    var actual = searchService.search("query", TENANT_ID);
    assertThat(actual).isEqualTo(expectedResult);
  }
}
