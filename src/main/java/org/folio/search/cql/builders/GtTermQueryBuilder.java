package org.folio.search.cql.builders;

import static org.opensearch.index.query.QueryBuilders.rangeQuery;

import java.util.Set;
import org.opensearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class GtTermQueryBuilder implements RangeTermQueryBuilder {

  @Override
  public QueryBuilder getQuery(Object term, String resource, String... fields) {
    return getRangeQuery(fields).gt(term);
  }

  @Override
  public QueryBuilder getTermLevelQuery(Object term, String fieldName, String resource, String fieldIndex) {
    return rangeQuery(fieldName).gt(term);
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of(">");
  }
}
