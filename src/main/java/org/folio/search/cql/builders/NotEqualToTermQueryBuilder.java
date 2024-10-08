package org.folio.search.cql.builders;

import static org.opensearch.index.query.MultiMatchQueryBuilder.Type.CROSS_FIELDS;
import static org.opensearch.index.query.Operator.AND;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.multiMatchQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.List;
import java.util.Set;
import org.folio.search.model.types.ResourceType;
import org.opensearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class NotEqualToTermQueryBuilder extends FulltextQueryBuilder {

  @Override
  public QueryBuilder getQuery(Object term, ResourceType resource, List<String> modifiers, String... fields) {
    return boolQuery().mustNot(multiMatchQuery(term, fields).operator(AND).type(CROSS_FIELDS));
  }

  @Override
  public QueryBuilder getFulltextQuery(Object term, String fieldName, ResourceType resource, List<String> modifiers) {
    return getQuery(term, resource, modifiers, updatePathForFulltextQuery(resource, fieldName));
  }

  @Override
  public QueryBuilder getTermLevelQuery(Object term, String fieldName, ResourceType resource, String fieldIndex) {
    return boolQuery().mustNot(termQuery(fieldName, term));
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of("<>");
  }
}
