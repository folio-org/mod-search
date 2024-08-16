package org.folio.search.cql.builders;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.ResourceType.UNKNOWN;
import static org.folio.search.utils.TestConstants.EMPTY_TERM_MODIFIERS;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.standardField;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.MultiMatchQueryBuilder.Type.CROSS_FIELDS;
import static org.opensearch.index.query.Operator.AND;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
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
import org.opensearch.index.query.QueryBuilder;

@UnitTest
@ExtendWith(MockitoExtension.class)
class AllTermQueryBuilderTest {

  @InjectMocks
  private AllTermQueryBuilder queryBuilder;
  @Mock
  private SearchFieldProvider searchFieldProvider;

  @Test
  void getQuery_positive() {
    var actual = queryBuilder.getQuery("value1 value2", UNKNOWN, EMPTY_TERM_MODIFIERS, "f1.*", "f2");
    assertThat(actual).isEqualTo(boolQuery()
      .must(getMultiMatchQuery("value1", "f1.*", "f2"))
      .must(getMultiMatchQuery("value2", "f1.*", "f2")));
  }

  @Test
  void getFulltextQuery_positive_multilangField() {
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, "field")).thenReturn(Optional.of(multilangField()));
    var actual = queryBuilder.getFulltextQuery("val", "field", UNKNOWN, emptyList());
    assertThat(actual).isEqualTo(getMultiMatchQuery("val", "field.*"));
  }

  @Test
  void getFulltextQuery_positive_standardField() {
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, "field")).thenReturn(Optional.of(standardField()));
    var actual = queryBuilder.getFulltextQuery("val", "field", UNKNOWN, emptyList());
    assertThat(actual).isEqualTo(getMultiMatchQuery("val", "field"));
  }

  @Test
  void getFulltextQuery_positive_standardFieldWithObject() {
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, "field")).thenReturn(Optional.of(standardField()));
    var actual = queryBuilder.getFulltextQuery(1234, "field", UNKNOWN, emptyList());
    assertThat(actual).isEqualTo(getMultiMatchQuery(1234, "field"));
  }

  @Test
  void getTermLevelQuery_positive() {
    var actual = queryBuilder.getTermLevelQuery("termValue", "field", UNKNOWN, null);
    assertThat(actual).isEqualTo(matchQuery("field", "termValue").operator(AND));
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactlyInAnyOrder("all", "adj");
  }

  private QueryBuilder getMultiMatchQuery(Object term, String... fieldNames) {
    return multiMatchQuery(term, fieldNames).operator(AND).type(CROSS_FIELDS);
  }
}
