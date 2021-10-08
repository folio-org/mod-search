package org.folio.search.cql.builders;

import static java.util.Arrays.asList;

import java.util.Set;
import org.elasticsearch.index.query.QueryBuilder;

public interface TermQueryBuilder {

  /**
   * Provides query for given term and field names.
   *
   * @param term - search term as {@link String} object
   * @param fields - resource fields name as {@code array} of {@link String} objects
   * @return Elasticsearch {@link QueryBuilder} object
   */
  default QueryBuilder getQuery(String term, String... fields) {
    throw unsupportedException(fields);
  }

  /**
   * Provides full-text query for given term and field name.
   *
   * @param term - search term as {@link String} object
   * @param fieldName - resource field name as {@link String} object
   * @return Elasticsearch {@link QueryBuilder} object
   */
  default QueryBuilder getMultilangQuery(String term, String fieldName) {
    throw unsupportedException(fieldName);
  }

  /**
   * Provides term-level query for given term and field name.
   *
   * @param term - search term as {@link String} object
   * @param fieldName - resource field name as {@link String} object
   * @param fieldIndex - field index mappings as {@link String} object
   * @return Elasticsearch {@link QueryBuilder} object
   */
  default QueryBuilder getTermLevelQuery(String term, String fieldName, String fieldIndex) {
    throw unsupportedException(fieldName);
  }

  /**
   * Provides set of supported comparators of CQL term node.
   *
   * @return set with supported comparators
   */
  Set<String> getSupportedComparators();

  default UnsupportedOperationException unsupportedException(String... fieldName) {
    return new UnsupportedOperationException(String.format(
      "Query is not supported yet [operator(s): %s, field(s): %s]", getSupportedComparators(), asList(fieldName)));
  }
}
