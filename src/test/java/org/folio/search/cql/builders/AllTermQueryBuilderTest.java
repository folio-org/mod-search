package org.folio.search.cql.builders;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.Operator.AND;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.standardField;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class AllTermQueryBuilderTest {

  @InjectMocks
  private AllTermQueryBuilder queryBuilder;
  @Mock
  private SearchFieldProvider searchFieldProvider;

  @Test
  void getQuery_positive() {
    var actual = queryBuilder.getQuery("value1 value2", RESOURCE_NAME, "f1.*", "f2");
    assertThat(actual).isEqualTo(boolQuery()
      .must(multiMatchQuery("value1", "f1.*", "f2"))
      .must(multiMatchQuery("value2", "f1.*", "f2")));
  }

  @Test
  void getFulltextQuery_positive_multilangField() {
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "field")).thenReturn(Optional.of(multilangField()));
    var actual = queryBuilder.getFulltextQuery("val", "field", RESOURCE_NAME, emptyList());
    assertThat(actual).isEqualTo(multiMatchQuery("val", "field.*"));
  }

  @Test
  void getFulltextQuery_positive_standardField() {
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "field")).thenReturn(Optional.of(standardField()));
    var actual = queryBuilder.getFulltextQuery("val", "field", RESOURCE_NAME, emptyList());
    assertThat(actual).isEqualTo(multiMatchQuery("val", "field"));
  }

  @Test
  void getFulltextQuery_positive_standardFieldWithObject() {
    when(searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "field")).thenReturn(Optional.of(standardField()));
    var actual = queryBuilder.getFulltextQuery(1234, "field", RESOURCE_NAME, emptyList());
    assertThat(actual).isEqualTo(multiMatchQuery(1234, "field").type(Type.CROSS_FIELDS).operator(AND));
  }

  @Test
  void getTermLevelQuery_positive() {
    var actual = queryBuilder.getTermLevelQuery("termValue", "field", RESOURCE_NAME, null);
    assertThat(actual).isEqualTo(matchQuery("field", "termValue").operator(AND));
  }

  @Test
  void getSupportedComparators_positive() {
    var actual = queryBuilder.getSupportedComparators();
    assertThat(actual).containsExactlyInAnyOrder("all", "adj");
  }
}
