package org.folio.search.cql.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.MultiMatchQueryBuilder.Type.PHRASE;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ExactTermQueryBuilderTest {

  private final TermQueryBuilder queryBuilder = new ExactTermQueryBuilder();

  @Test
  void getQuery_positive() {
    var actual = queryBuilder.getQuery("value", "f1.*", "f2");
    assertThat(actual).isEqualTo(multiMatchQuery("value", "f1.*", "f2").type(PHRASE));
  }

  @Test
  void getFullTextQuery_positive() {
    var actual = queryBuilder.getMultilangQuery("val", "field");
    assertThat(actual).isEqualTo(multiMatchQuery("val", "field.*").type(PHRASE));
  }

  @Test
  void getTermLevelQuery_positive() {
    var actual = queryBuilder.getTermLevelQuery("termValue", "field");
    assertThat(actual).isEqualTo(termQuery("field", "termValue"));
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactly("==");
  }
}
