package org.folio.search.cql;

import org.folio.search.exception.SearchServiceException;
import org.springframework.stereotype.Component;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParser;

@Component
public class CqlQueryParser {

  /**
   * Parses given CQL query and resource to the {@link CQLNode} object.
   *
   * @param query    - CQL query to parse
   * @param resource - resource name to provide meaningful error descriptions.
   * @return parsed query as {@link CQLNode} object
   */
  public CQLNode parseCqlQuery(String query, String resource) {
    try {
      return new CQLParser().parse(query);
    } catch (Exception e) {
      throw new SearchServiceException(String.format(
        "Failed to parse cql query [cql: '%s', resource: %s]", query, resource), e);
    }
  }
}
