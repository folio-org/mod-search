package org.folio.search.cql.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.MultiMatchQueryBuilder.Type.PHRASE;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.elasticsearch.index.query.QueryBuilders.scriptQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import org.elasticsearch.script.Script;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ExactTermQueryBuilderTest {

  private final ExactTermQueryBuilder queryBuilder = new ExactTermQueryBuilder();

  @Test
  void getQuery_positive() {
    var actual = queryBuilder.getQuery("value", "f1.*", "f2");
    assertThat(actual).isEqualTo(multiMatchQuery("value", "f1.*", "f2").type(PHRASE));
  }

  @Test
  void getMultilangQuery_positive() {
    var actual = queryBuilder.getMultilangQuery("val", "field");
    assertThat(actual).isEqualTo(multiMatchQuery("val", "field.*").type(PHRASE));
  }

  @Test
  void getMultilangQuery_positive_emptyTermValue() {
    var actual = queryBuilder.getMultilangQuery("", "field");
    assertThat(actual).isEqualTo(termQuery("plain_field", ""));
  }

  @Test
  void getMultilangQuery_positive_emptyArrayValue() {
    var actual = queryBuilder.getMultilangQuery("[]", "field");
    assertThat(actual).isEqualTo(scriptQuery(new Script("doc['plain_field'].size() == 0")));
  }

  @Test
  void getTermLevelQuery_positive() {
    var actual = queryBuilder.getTermLevelQuery("termValue", "field", null);
    assertThat(actual).isEqualTo(termQuery("field", "termValue"));
  }

  @Test
  void getTermLevelQuery_positive_emptyArrayValueKeywordField() {
    var actual = queryBuilder.getTermLevelQuery("[]", "field", "keyword");
    assertThat(actual).isEqualTo(scriptQuery(new Script("doc['field'].size() == 0")));
  }

  @Test
  void getTermLevelQuery_positive_emptyArrayValueNonKeywordField() {
    var actual = queryBuilder.getTermLevelQuery("[]", "field", null);
    assertThat(actual).isEqualTo(termQuery("field", "[]"));
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactly("==");
  }
}
