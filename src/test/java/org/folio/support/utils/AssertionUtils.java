package org.folio.support.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.search.sort.FieldSortBuilder;
import org.opensearch.search.sort.SortOrder;

@UtilityClass
public class AssertionUtils {

  /**
   * Asserts that request contains expected parameters.
   *
   * @param actual request to be checked
   * @param expectedQuery expected query filter
   * @param expectedLimit expected limit value
   * @param expectedOffset expected offset value
   * @param expectedSortBy expected sort field name
   */
  public static void assertRequest(SearchRequest actual, TermQueryBuilder expectedQuery,
                                   int expectedLimit, int expectedOffset, String expectedSortBy) {
    var source = actual.source();
    assertThat(source.size()).isEqualTo(expectedLimit);
    assertThat(source.from()).isEqualTo(expectedOffset);
    assertThat(source.sorts()).hasSize(1);
    assertThat(source.sorts().getFirst()).isInstanceOf(FieldSortBuilder.class);

    var sort = (FieldSortBuilder) source.sorts().getFirst();
    assertThat(sort.getFieldName()).isEqualTo(expectedSortBy);
    assertThat(sort.order()).isEqualTo(SortOrder.ASC);
    assertThat(source.query()).isInstanceOf(BoolQueryBuilder.class);

    var query = (BoolQueryBuilder) source.query();
    assertThat(query.filter()).isEqualTo(List.of(expectedQuery));
  }
}
