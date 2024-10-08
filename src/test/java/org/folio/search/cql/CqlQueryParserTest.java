package org.folio.search.cql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.z3950.zing.cql.CQLRelation;
import org.z3950.zing.cql.CQLTermNode;

@UnitTest
class CqlQueryParserTest {

  private final CqlQueryParser cqlQueryParser = new CqlQueryParser();

  @Test
  void parseCqlQuery_positive() {
    var actual = cqlQueryParser.parseCqlQuery("title all book", ResourceType.UNKNOWN);
    assertThat(actual.toCQL()).isEqualTo(new CQLTermNode("title", new CQLRelation("all"), "book").toCQL());
  }

  @Test
  void parseCqlQuery_negative() {
    assertThatThrownBy(() -> cqlQueryParser.parseCqlQuery("> invalidQuery", ResourceType.UNKNOWN))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Failed to parse cql query [cql: '> invalidQuery', resource: unknown]");
  }
}
