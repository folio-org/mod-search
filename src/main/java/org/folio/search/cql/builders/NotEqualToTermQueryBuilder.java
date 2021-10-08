package org.folio.search.cql.builders;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.util.Set;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Component;

@Component
public class NotEqualToTermQueryBuilder implements TermQueryBuilder {

  @Override
  public QueryBuilder getTermLevelQuery(String term, String fieldName, String fieldIndex) {
    return QueryBuilders.boolQuery().mustNot(termQuery(fieldName, term));
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of("<>");
  }
}
