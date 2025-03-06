package org.folio.search.cql.builders;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.ResourceType.UNKNOWN;
import static org.folio.search.utils.TestConstants.EMPTY_TERM_MODIFIERS;
import static org.folio.search.utils.TestConstants.STRING_TERM_MODIFIERS;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.standardField;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.MultiMatchQueryBuilder.Type.PHRASE;
import static org.opensearch.index.query.QueryBuilders.multiMatchQuery;
import static org.opensearch.index.query.QueryBuilders.scriptQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.index.query.QueryBuilders.termsQuery;

import java.util.List;
import java.util.Optional;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.script.Script;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ExactTermQueryBuilderTest {

  @InjectMocks
  private ExactTermQueryBuilder queryBuilder;
  @Mock
  private SearchFieldProvider searchFieldProvider;

  @Test
  void getQuery_positive() {
    var actual = queryBuilder.getQuery("value", UNKNOWN, EMPTY_TERM_MODIFIERS, "f1.*", "f2");
    assertThat(actual).isEqualTo(multiMatchQuery("value", "f1.*", "f2").type(PHRASE));
  }

  @Test
  void getQuery_positive_stringModifier() {
    var actual = queryBuilder.getQuery("value", UNKNOWN, STRING_TERM_MODIFIERS, "f1.*", "f2");
    assertThat(actual).isEqualTo(multiMatchQuery("value", "plain_f1", "f2").type(PHRASE));
  }

  @Test
  void getFulltextQuery_positive() {
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, "field")).thenReturn(Optional.of(multilangField()));
    var actual = queryBuilder.getFulltextQuery("val", "field", UNKNOWN, emptyList());
    assertThat(actual).isEqualTo(multiMatchQuery("val", "field.*").type(PHRASE));
  }

  @Test
  void getFulltextQuery_positive_standardField() {
    when(searchFieldProvider.getPlainFieldByPath(UNKNOWN, "field")).thenReturn(Optional.of(standardField()));
    var actual = queryBuilder.getFulltextQuery("val", "field", UNKNOWN, emptyList());
    assertThat(actual).isEqualTo(multiMatchQuery("val", "field").type(PHRASE));
  }

  @Test
  void getFulltextQuery_positive_emptyTermValue() {
    var actual = queryBuilder.getFulltextQuery("", "field", UNKNOWN, emptyList());
    assertThat(actual).isEqualTo(termQuery("plain_field", ""));
  }

  @Test
  void getFulltextQuery_positive_stringModifier() {
    var actual = queryBuilder.getFulltextQuery("val", "field", UNKNOWN, List.of("string"));
    assertThat(actual).isEqualTo(termQuery("plain_field", "val"));
  }

  @Test
  void getFulltextQuery_positive_emptyArrayValue() {
    var actual = queryBuilder.getFulltextQuery("[]", "field", UNKNOWN, emptyList());
    assertThat(actual).isEqualTo(scriptQuery(new Script("doc['plain_field'].size() == 0")));
  }

  @Test
  void getTermLevelQuery_positive() {
    var actual = queryBuilder.getTermLevelQuery("termValue", "field", UNKNOWN, null);
    assertThat(actual).isEqualTo(termQuery("field", "termValue"));
  }

  @Test
  void getTermLevelQuery_positive_emptyArrayValueKeywordField() {
    var actual = queryBuilder.getTermLevelQuery("[]", "field", UNKNOWN, "keyword");
    assertThat(actual).isEqualTo(scriptQuery(new Script("doc['field'].size() == 0")));
  }

  @Test
  void getTermLevelQuery_positive_emptyArrayValueNonKeywordField() {
    var actual = queryBuilder.getTermLevelQuery("[]", "field", UNKNOWN, null);
    assertThat(actual).isEqualTo(termQuery("field", "[]"));
  }

  @Test
  void getTermLevelQuery_positive_arrayOfTermsWithSizeLessThen1() {
    var actual = queryBuilder.getTermLevelQuery(new String[]{"val1"}, "field", UNKNOWN, null);
    assertThat(actual).isEqualTo(termQuery("field", "val1"));
  }


  @Test
  void getTermLevelQuery_positive_arrayOfTermsWithSizeGreaterThen1() {
    var actual = queryBuilder.getTermLevelQuery(new String[]{"val1", "val2"}, "field", UNKNOWN, null);
    assertThat(actual).isEqualTo(termsQuery("field", "val1", "val2"));
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactly("==");
  }
}
