package org.folio.search.cql.builders;

import static java.util.Arrays.asList;

import java.util.Set;
import org.elasticsearch.index.query.QueryBuilder;

public interface TermQueryBuilder {

  /**
   * Provides query for given term and field names.
   *
   * @param term - search term as {@link String} object
   * @param resource - resource name for querying as {@link String object}
   * @param fields - resource fields name as {@code array} of {@link String} objects
   * @return Elasticsearch {@link QueryBuilder} object
   */
  default QueryBuilder getQuery(Object term, String resource, String... fields) {
    throw unsupportedException(fields);
  }

  /**
   * Provides full-text query for given term and field name.
   *
   * @param term - search term as {@link String} object
   * @param fieldName - resource field name as {@link String} object
   * @param resource - resource name for querying as {@link String object}
   * @return Elasticsearch {@link QueryBuilder} object
   */
  default QueryBuilder getFulltextQuery(Object term, String fieldName, String resource) {
    throw unsupportedException(fieldName);
  }

  /**
   * Provides term-level query for given term and field name.
   *
   * @param term - search term as {@link String} object
   * @param fieldName - resource field name as {@link String} object
   * @param resource - resource name for querying as {@link String object}
   * @param fieldIndex - field index mappings as {@link String} object
   * @return Elasticsearch {@link QueryBuilder} object
   */
  default QueryBuilder getTermLevelQuery(Object term, String fieldName, String resource, String fieldIndex) {
    throw unsupportedException(fieldName);
  }

  /**
   * Provides set of supported comparators of CQL term node.
   *
   * @return set with supported comparators
   */
  Set<String> getSupportedComparators();

  /**
   * Creates {@link  UnsupportedOperationException} object.
   *
   * @param fieldNames - list of fields from term query builder implementation
   * @return created {@link  UnsupportedOperationException} object
   */
  default UnsupportedOperationException unsupportedException(String... fieldNames) {
    return new UnsupportedOperationException(String.format(
      "Query is not supported yet [operator(s): %s, field(s): %s]", getSupportedComparators(), asList(fieldNames)));
  }
}
