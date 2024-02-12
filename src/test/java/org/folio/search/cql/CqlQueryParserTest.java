package org.folio.search.cql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;

import org.folio.search.exception.SearchServiceException;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.z3950.zing.cql.CQLRelation;
import org.z3950.zing.cql.CQLTermNode;

@UnitTest
class CqlQueryParserTest {

  private final CqlQueryParser cqlQueryParser = new CqlQueryParser();

  @Test
  void parseCqlQuery_positive() {
    var actual = cqlQueryParser.parseCqlQuery("title all book", RESOURCE_NAME);
    assertThat(actual.toCQL()).isEqualTo(new CQLTermNode("title", new CQLRelation("all"), "book").toCQL());
  }

  @Test
  void parseCqlQuery_negative() {
    assertThatThrownBy(() -> cqlQueryParser.parseCqlQuery("> invalidQuery", RESOURCE_NAME))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Failed to parse cql query [cql: '> invalidQuery', resource: test-resource]");
  }
}
