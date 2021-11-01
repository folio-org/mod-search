package org.folio.search.cql.builders;

import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

import java.util.Set;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class LteTermQueryBuilder implements TermQueryBuilder {

  @Override
  public QueryBuilder getTermLevelQuery(String term, String fieldName, String resource, String fieldIndex) {
    return rangeQuery(fieldName).lte(term);
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of("<=");
  }
}
