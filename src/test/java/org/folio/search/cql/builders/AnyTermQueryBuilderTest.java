package org.folio.search.cql.builders;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.ResourceType.UNKNOWN;
import static org.folio.search.utils.TestConstants.EMPTY_TERM_MODIFIERS;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.standardField;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.matchQuery;
import static org.opensearch.index.query.QueryBuilders.multiMatchQuery;

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
class AnyTermQueryBuilderTest {

  @InjectMocks
  private AnyTermQueryBuilder queryBuilder;
  @Mock
  private SearchFieldProvider searchFieldProvider;

  @Test
  void getQuery_positive() {
    var actual = queryBuilder.getQuery("value", UNKNOWN, EMPTY_TERM_MODIFIERS, "f1.*", "f2");
    assertThat(actual).isEqualTo(multiMatchQuery("value", "f1.*", "f2"));
  }

  @Test
  void getFulltextQuery_positive_multilangField() {
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, "field")).thenReturn(Optional.of(multilangField()));
    var actual = queryBuilder.getFulltextQuery("val", "field", UNKNOWN, emptyList());
    assertThat(actual).isEqualTo(multiMatchQuery("val", "field.*"));
  }

  @Test
  void getFulltextQuery_positive_standardField() {
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, "field")).thenReturn(Optional.of(standardField()));
    var actual = queryBuilder.getFulltextQuery("val", "field", UNKNOWN, emptyList());
    assertThat(actual).isEqualTo(multiMatchQuery("val", "field"));
  }

  @Test
  void getTermLevelQuery_positive() {
    var actual = queryBuilder.getTermLevelQuery("termValue", "field", UNKNOWN, null);
    assertThat(actual).isEqualTo(matchQuery("field", "termValue"));
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactly("any");
  }
}
