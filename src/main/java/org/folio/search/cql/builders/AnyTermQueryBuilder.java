package org.folio.search.cql.builders;

import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;

import java.util.List;
import java.util.Set;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class AnyTermQueryBuilder extends FulltextQueryBuilder {

  @Override
  public QueryBuilder getQuery(Object term, String resource, String... fields) {
    return multiMatchQuery(term, fields);
  }

  @Override
  public QueryBuilder getFulltextQuery(Object term, String fieldName, String resource, List<String> modifiers) {
    return getQuery(term, resource, updatePathForFulltextQuery(resource, fieldName));
  }

  @Override
  public QueryBuilder getTermLevelQuery(Object term, String fieldName, String resource, String fieldIndex) {
    return matchQuery(fieldName, term);
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of("any");
  }
}
