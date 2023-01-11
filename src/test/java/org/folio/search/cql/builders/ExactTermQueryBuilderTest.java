package org.folio.search.cql.builders;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.standardField;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.MultiMatchQueryBuilder.Type.PHRASE;
import static org.opensearch.index.query.QueryBuilders.multiMatchQuery;
import static org.opensearch.index.query.QueryBuilders.scriptQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.List;
import java.util.Optional;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.spring.test.type.UnitTest;
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
    var actual = queryBuilder.getQuery("value", RESOURCE_NAME, "f1.*", "f2");
    assertThat(actual).isEqualTo(multiMatchQuery("value", "f1.*", "f2").type(PHRASE));
  }

  @Test
  void getFulltextQuery_positive() {
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "field")).thenReturn(Optional.of(multilangField()));
    var actual = queryBuilder.getFulltextQuery("val", "field", RESOURCE_NAME, emptyList());
    assertThat(actual).isEqualTo(multiMatchQuery("val", "field.*").type(PHRASE));
  }

  @Test
  void getFulltextQuery_positive_standardField() {
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "field")).thenReturn(Optional.of(standardField()));
    var actual = queryBuilder.getFulltextQuery("val", "field", RESOURCE_NAME, emptyList());
    assertThat(actual).isEqualTo(multiMatchQuery("val", "field").type(PHRASE));
  }

  @Test
  void getFulltextQuery_positive_emptyTermValue() {
    var actual = queryBuilder.getFulltextQuery("", "field", RESOURCE_NAME, emptyList());
    assertThat(actual).isEqualTo(termQuery("plain_field", ""));
  }

  @Test
  void getFulltextQuery_positive_stringModifier() {
    var actual = queryBuilder.getFulltextQuery("val", "field", RESOURCE_NAME, List.of("string"));
    assertThat(actual).isEqualTo(termQuery("plain_field", "val"));
  }

  @Test
  void getFulltextQuery_positive_emptyArrayValue() {
    var actual = queryBuilder.getFulltextQuery("[]", "field", RESOURCE_NAME, emptyList());
    assertThat(actual).isEqualTo(scriptQuery(new Script("doc['plain_field'].size() == 0")));
  }

  @Test
  void getTermLevelQuery_positive() {
    var actual = queryBuilder.getTermLevelQuery("termValue", "field", RESOURCE_NAME, null);
    assertThat(actual).isEqualTo(termQuery("field", "termValue"));
  }

  @Test
  void getTermLevelQuery_positive_emptyArrayValueKeywordField() {
    var actual = queryBuilder.getTermLevelQuery("[]", "field", RESOURCE_NAME, "keyword");
    assertThat(actual).isEqualTo(scriptQuery(new Script("doc['field'].size() == 0")));
  }

  @Test
  void getTermLevelQuery_positive_emptyArrayValueNonKeywordField() {
    var actual = queryBuilder.getTermLevelQuery("[]", "field", RESOURCE_NAME, null);
    assertThat(actual).isEqualTo(termQuery("field", "[]"));
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactly("==");
  }
}
