package org.folio.search.cql;

import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.types.ResourceType;
import org.springframework.stereotype.Component;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParser;

@Log4j2
@Component
public class CqlQueryParser {

  /**
   * Parses given CQL query and resource to the {@link CQLNode} object.
   *
   * @param query    - CQL query to parse
   * @param resource - resource name to provide meaningful error descriptions.
   * @return parsed query as {@link CQLNode} object
   */
  public CQLNode parseCqlQuery(String query, ResourceType resource) {
    log.debug("parseCqlQuery::Parsing CQL query [cql: '{}', resource: {}]", query, resource.getName());
    try {
      return new CQLParser().parse(query);
    } catch (Exception e) {
      throw new SearchServiceException(String.format(
        "Failed to parse CQL query [cql: '%s', resource: %s]", query, resource.getName()), e);
    }
  }
}
