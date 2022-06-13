package org.folio.search.cql.builders;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.opensearch.index.query.MultiMatchQueryBuilder.Type.CROSS_FIELDS;
import static org.opensearch.index.query.Operator.AND;
import static org.opensearch.index.query.QueryBuilders.existsQuery;
import static org.opensearch.index.query.QueryBuilders.matchQuery;
import static org.opensearch.index.query.QueryBuilders.multiMatchQuery;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.standardField;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EqualTermQueryBuilderTest {

  @InjectMocks
  private EqualTermQueryBuilder queryBuilder;
  @Mock
  private SearchFieldProvider searchFieldProvider;

  @Test
  void getQuery_positive() {
    var actual = queryBuilder.getQuery("value", RESOURCE_NAME, "f1.*", "f2");
    assertThat(actual).isEqualTo(multiMatchQuery("value", "f1.*", "f2").operator(AND).type(CROSS_FIELDS));
  }

  @Test
  void getQuery_positive_emptyValueAndSingleMultilangFieldWithAlias() {
    var actual = queryBuilder.getQuery("", RESOURCE_NAME, "f1.*");
    assertThat(actual).isEqualTo(existsQuery("plain_f1"));
  }

  @Test
  void getQuery_positive_emptyValueAndSingleKeywordField() {
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "field")).thenReturn(Optional.of(keywordField()));
    var actual = queryBuilder.getQuery("", RESOURCE_NAME, "field");
    assertThat(actual).isEqualTo(existsQuery("field"));
  }

  @Test
  void getFulltextQuery_positive_multilangField() {
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "field")).thenReturn(Optional.of(multilangField()));
    var actual = queryBuilder.getFulltextQuery("val", "field", RESOURCE_NAME, emptyList());
    assertThat(actual).isEqualTo(multiMatchQuery("val", "field.*").operator(AND).type(CROSS_FIELDS));
  }

  @Test
  void getFulltextQuery_positive_standardField() {
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "field")).thenReturn(Optional.of(standardField()));
    var actual = queryBuilder.getFulltextQuery("val", "field", RESOURCE_NAME, emptyList());
    assertThat(actual).isEqualTo(multiMatchQuery("val", "field").operator(AND).type(CROSS_FIELDS));
  }

  @Test
  void getFulltextQuery_positive_emptyTermValue() {
    var actual = queryBuilder.getFulltextQuery("", "field", RESOURCE_NAME, emptyList());
    assertThat(actual).isEqualTo(existsQuery("plain_field"));
  }

  @Test
  void getTermLevelQuery_positive() {
    var actual = queryBuilder.getTermLevelQuery("termValue", "field", RESOURCE_NAME, null);
    assertThat(actual).isEqualTo(matchQuery("field", "termValue").operator(AND));
  }

  @Test
  void getTermLevelQuery_positive_checkIfFieldExists() {
    var actual = queryBuilder.getTermLevelQuery("", "field", RESOURCE_NAME, null);
    assertThat(actual).isEqualTo(existsQuery("field"));
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactly("=");
  }
}
