package org.folio.search.cql.builders;

import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

import java.util.Set;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class GtTermQueryBuilder implements TermQueryBuilder {

  @Override
  public QueryBuilder getTermLevelQuery(String term, String fieldName) {
    return rangeQuery(fieldName).gt(term);
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of(">");
  }
}
