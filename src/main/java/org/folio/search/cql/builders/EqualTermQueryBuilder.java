package org.folio.search.cql.builders;

import static org.elasticsearch.index.query.MultiMatchQueryBuilder.Type.CROSS_FIELDS;
import static org.elasticsearch.index.query.Operator.AND;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.folio.search.utils.SearchUtils.getPathToFulltextPlainValue;

import java.util.Set;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class EqualTermQueryBuilder extends FulltextQueryBuilder {

  @Override
  public QueryBuilder getQuery(String term, String resource, String... fields) {
    return multiMatchQuery(term, fields).operator(AND).type(CROSS_FIELDS);
  }

  @Override
  public QueryBuilder getFulltextQuery(String term, String fieldName, String resource) {
    if (term.isEmpty()) {
      return existsQuery(getPathToFulltextPlainValue(fieldName));
    }
    return getQuery(term, resource, updatePathForFulltextQuery(resource, fieldName));
  }

  @Override
  public QueryBuilder getTermLevelQuery(String term, String fieldName, String resource, String fieldIndex) {
    return term.isEmpty() ? existsQuery(fieldName) : matchQuery(fieldName, term).operator(AND);
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of("=");
  }
}
