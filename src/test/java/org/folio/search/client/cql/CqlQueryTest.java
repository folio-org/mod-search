package org.folio.search.client.cql;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.client.CqlQuery.exactMatchAny;
import static org.folio.search.model.client.CqlQuery.greaterThan;

import java.util.List;
import org.folio.search.model.client.CqlQuery;
import org.folio.search.model.client.CqlQueryParam;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class CqlQueryTest {

  @Test
  void exactMatchAny_positive_oneValue() {
    assertThat(exactMatchAny(CqlQueryParam.NAME, List.of("id1")))
      .hasToString("name==(\"id1\")");
  }

  @Test
  void exactMatchAny_positive_moreThanOneValue() {
    assertThat(exactMatchAny(CqlQueryParam.NAME, asList("id1", "id2")))
      .hasToString("name==(\"id1\" or \"id2\")");
  }

  @Test
  void greaterThan_positive() {
    assertThat(greaterThan(CqlQueryParam.ID, "1234"))
      .hasToString("id>(1234)");
  }

  @Test
  void sortBy_positive() {
    var cql = CqlQuery.exactMatchAny(CqlQueryParam.NAME, List.of("abcd"));

    assertThat(CqlQuery.sortBy(cql, CqlQueryParam.ID))
      .hasToString("name==(\"abcd\") sortBy id");
  }

  @Test
  void exactMatchAny_shouldFilterOutEmptyString() {
    assertThat(exactMatchAny(CqlQueryParam.NAME, asList("id1", null, "")))
      .hasToString("name==(\"id1\")");
  }
}
