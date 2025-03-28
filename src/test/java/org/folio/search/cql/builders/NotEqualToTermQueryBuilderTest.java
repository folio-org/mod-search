package org.folio.search.cql.builders;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.ResourceType.UNKNOWN;
import static org.folio.support.TestConstants.EMPTY_TERM_MODIFIERS;
import static org.folio.support.utils.TestUtils.keywordField;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.MultiMatchQueryBuilder.Type.CROSS_FIELDS;
import static org.opensearch.index.query.Operator.AND;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.multiMatchQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.Optional;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class NotEqualToTermQueryBuilderTest {

  @InjectMocks
  private NotEqualToTermQueryBuilder queryBuilder;
  @Mock
  private SearchFieldProvider searchFieldProvider;

  @Test
  void getQuery_positive() {
    var actual = queryBuilder.getQuery("value", UNKNOWN, EMPTY_TERM_MODIFIERS, "f1.*", "f2");
    assertThat(actual).isEqualTo(boolQuery()
      .mustNot(multiMatchQuery("value", "f1.*", "f2").operator(AND).type(CROSS_FIELDS)));
  }

  @Test
  void getFulltextQuery_positive() {
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, "field")).thenReturn(Optional.of(keywordField()));
    var actual = queryBuilder.getFulltextQuery("val", "field", UNKNOWN, emptyList());
    assertThat(actual).isEqualTo(boolQuery()
      .mustNot(multiMatchQuery("val", "field").operator(AND).type(CROSS_FIELDS)));
  }

  @Test
  void getTermLevelQuery_positive() {
    var actual = queryBuilder.getTermLevelQuery("termValue", "field", UNKNOWN, null);
    assertThat(actual).isEqualTo(boolQuery().mustNot(termQuery("field", "termValue")));
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactly("<>");
  }
}
