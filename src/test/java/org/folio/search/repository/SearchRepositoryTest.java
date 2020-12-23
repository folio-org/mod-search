package org.folio.search.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.folio.search.utils.TestConstants.TENANT_ID;

import org.folio.search.model.rest.response.SearchResult;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchRepositoryTest {

  @InjectMocks private SearchRepository searchRepository;

  @Test
  void search_positive() {
    var actual = searchRepository.search(matchAllQuery(), TENANT_ID);
    assertThat(actual).isEqualTo(new SearchResult());
  }
}