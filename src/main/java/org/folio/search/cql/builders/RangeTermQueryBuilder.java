package org.folio.search.cql.builders;

import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

import org.elasticsearch.index.query.RangeQueryBuilder;

public interface RangeTermQueryBuilder extends TermQueryBuilder {

  /**
   * Creates range query for fields with aliases.
   *
   * <p>Currently this approach supports only single value aliases (call-number browsing).</p>
   *
   * @param fields - array with field names as {@link String} objects
   * @return created {@link RangeQueryBuilder} object.
   */
  default RangeQueryBuilder getRangeQuery(String... fields) {
    if (fields.length != 1) {
      throw unsupportedException(fields);
    }

    return rangeQuery(fields[0]);
  }
}
