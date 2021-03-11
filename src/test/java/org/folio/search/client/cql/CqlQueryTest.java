package org.folio.search.client.cql;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.client.cql.CqlQuery.exactMatchAny;

import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class CqlQueryTest {
  @Test
  void exactMatchAny_positive() {
    assertThat(exactMatchAny("id", asList("id1", "id2")).toString())
      .isEqualTo("id==(\"id1\" or \"id2\")");
  }

  @Test
  void exactMatchAny_shouldFilterOutEmptyString() {
    assertThat(exactMatchAny("id", asList("id1", null, "")).toString())
      .isEqualTo("id==(\"id1\")");
  }
}
