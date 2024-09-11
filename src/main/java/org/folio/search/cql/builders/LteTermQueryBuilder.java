package org.folio.search.cql.builders;

import static org.opensearch.index.query.QueryBuilders.rangeQuery;

import java.util.List;
import java.util.Set;
import org.folio.search.model.types.ResourceType;
import org.opensearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class LteTermQueryBuilder implements RangeTermQueryBuilder {

  @Override
  public QueryBuilder getQuery(Object term, ResourceType resource, List<String> modifiers, String... fields) {
    return getRangeQuery(fields).lte(term);
  }

  @Override
  public QueryBuilder getTermLevelQuery(Object term, String fieldName, ResourceType resource, String fieldIndex) {
    return rangeQuery(fieldName).lte(term);
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of("<=");
  }
}
