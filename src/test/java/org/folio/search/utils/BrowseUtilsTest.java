package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;

import org.folio.search.cql.CqlQueryParser;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@UnitTest
class BrowseUtilsTest {

  @ParameterizedTest
  @CsvSource({
    "callNumber > A, A",
    "callNumber >= A, A",
    "callNumber < A, A",
    "field < A, ",
    "callNumber >= A or callNumber < A, A",
    "title >= B or callNumber < A, A",
    "callNumber < A or title >= B, A",
    "callNumber > A sortby title, "
  })
  void getAnchorCallNumberPositive(String query, String expected) {
    var node = new CqlQueryParser().parseCqlQuery(query, RESOURCE_NAME);
    var actual = BrowseUtils.getAnchorCallNumber(node);
    assertThat(actual).isEqualTo(expected);
  }
}
