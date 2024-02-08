package org.folio.search.client.cql;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.client.CqlQuery.exactMatchAny;

import java.util.List;
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
  void exactMatchAny_shouldFilterOutEmptyString() {
    assertThat(exactMatchAny(CqlQueryParam.NAME, asList("id1", null, "")))
      .hasToString("name==(\"id1\")");
  }
}
