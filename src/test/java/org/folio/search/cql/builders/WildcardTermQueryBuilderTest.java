package org.folio.search.cql.builders;

import static java.util.Collections.emptyList;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.EMPTY_TERM_MODIFIERS;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.standardField;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.boolQuery;

import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.WildcardQueryBuilder;

@UnitTest
@ExtendWith(MockitoExtension.class)
class WildcardTermQueryBuilderTest {

  @InjectMocks
  private WildcardTermQueryBuilder queryBuilder;
  @Mock
  private SearchFieldProvider searchFieldProvider;

  private static WildcardQueryBuilder wildcardQuery(String field, String term) {
    return QueryBuilders.wildcardQuery(field, term).rewrite("constant_score");
  }

  @Test
  void getQuery_positive() {
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME,
      "contributors.name")).thenReturn(of(standardField()));
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "f2")).thenReturn(of(keywordField()));
    var actual = queryBuilder.getQuery("value*", RESOURCE_NAME,
      EMPTY_TERM_MODIFIERS, "f1.*", "f2", "contributors.name");
    assertThat(actual).isEqualTo(boolQuery()
      .should(wildcardQuery("plain_f1", "value*"))
      .should(wildcardQuery("f2", "value*"))
      .should(wildcardQuery("contributors.plain_name", "value*")));
  }

  @Test
  void getQuery_positive_singleMultilangFieldInGroup() {
    var actual = queryBuilder.getQuery("*value*", RESOURCE_NAME, EMPTY_TERM_MODIFIERS, "f1.*");
    assertThat(actual).isEqualTo(wildcardQuery("plain_f1", "*value*"));
  }

  @Test
  void getQuery_positive_singleStandardFieldInGroup() {
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "field")).thenReturn(of(standardField()));
    var actual = queryBuilder.getQuery("*value*", RESOURCE_NAME, EMPTY_TERM_MODIFIERS, "field");
    assertThat(actual).isEqualTo(wildcardQuery("plain_field", "*value*"));
  }

  @Test
  void getFulltextQuery_positive() {
    var actual = queryBuilder.getFulltextQuery("val*", "field", RESOURCE_NAME, emptyList());
    assertThat(actual).isEqualTo(wildcardQuery("plain_field", "val*"));
  }

  @Test
  void getTermLevelQuery_positive() {
    var actual = queryBuilder.getTermLevelQuery("termValue*", "field", RESOURCE_NAME, null);
    assertThat(actual).isEqualTo(wildcardQuery("field", "termValue*"));
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactly("wildcard");
  }
}
