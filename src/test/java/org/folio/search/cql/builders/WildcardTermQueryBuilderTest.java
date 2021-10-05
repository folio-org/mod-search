package org.folio.search.cql.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.WildcardQueryBuilder;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class WildcardTermQueryBuilderTest {

  private final TermQueryBuilder queryBuilder = new WildcardTermQueryBuilder();

  @Test
  void getQuery_positive() {
    var actual = queryBuilder.getQuery("value*", "f1.*", "f2");
    assertThat(actual).isEqualTo(boolQuery()
      .should(wildcardQuery("plain_f1", "value*"))
      .should(wildcardQuery("f2", "value*")));
  }

  @Test
  void getQuery_positive_singleFieldInGroup() {
    var actual = queryBuilder.getQuery("*value*", "f1.*");
    assertThat(actual).isEqualTo(wildcardQuery("plain_f1", "*value*"));
  }

  @Test
  void getFullTextQuery_positive() {
    var actual = queryBuilder.getMultilangQuery("val*", "field");
    assertThat(actual).isEqualTo(wildcardQuery("plain_field", "val*"));
  }

  @Test
  void getTermLevelQuery_positive() {
    var actual = queryBuilder.getTermLevelQuery("termValue*", "field");
    assertThat(actual).isEqualTo(wildcardQuery("field", "termValue*"));
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactly("wildcard");
  }

  private static WildcardQueryBuilder wildcardQuery(String field, String term) {
    return QueryBuilders.wildcardQuery(field, term).rewrite("constant_score");
  }
}
