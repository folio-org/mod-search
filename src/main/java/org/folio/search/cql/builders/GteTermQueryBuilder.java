package org.folio.search.cql.builders;

import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

import java.util.Set;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class GteTermQueryBuilder implements TermQueryBuilder {

  @Override
  public QueryBuilder getTermLevelQuery(Object term, String fieldName, String resource, String fieldIndex) {
    return rangeQuery(fieldName).gte(term);
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of(">=");
  }
}
