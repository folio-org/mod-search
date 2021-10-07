package org.folio.search.cql.builders;

import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.folio.search.utils.SearchUtils.getPathForMultilangField;

import java.util.Set;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class AnyTermQueryBuilder implements TermQueryBuilder {

  @Override
  public QueryBuilder getQuery(String term, String... fields) {
    return multiMatchQuery(term, fields);
  }

  @Override
  public QueryBuilder getMultilangQuery(String term, String fieldName) {
    return getQuery(term, getPathForMultilangField(fieldName));
  }

  @Override
  public QueryBuilder getTermLevelQuery(String term, String fieldName, String fieldIndex) {
    return matchQuery(fieldName, term);
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of("any");
  }
}
