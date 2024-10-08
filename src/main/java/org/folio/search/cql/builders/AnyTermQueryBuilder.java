package org.folio.search.cql.builders;

import static org.opensearch.index.query.QueryBuilders.matchQuery;
import static org.opensearch.index.query.QueryBuilders.multiMatchQuery;

import java.util.List;
import java.util.Set;
import org.folio.search.model.types.ResourceType;
import org.opensearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class AnyTermQueryBuilder extends FulltextQueryBuilder {

  @Override
  public QueryBuilder getQuery(Object term, ResourceType resource, List<String> modifiers, String... fields) {
    return multiMatchQuery(term, fields);
  }

  @Override
  public QueryBuilder getFulltextQuery(Object term, String fieldName, ResourceType resource, List<String> modifiers) {
    return getQuery(term, resource, modifiers, updatePathForFulltextQuery(resource, fieldName));
  }

  @Override
  public QueryBuilder getTermLevelQuery(Object term, String fieldName, ResourceType resource, String fieldIndex) {
    return matchQuery(fieldName, term);
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of("any");
  }
}
