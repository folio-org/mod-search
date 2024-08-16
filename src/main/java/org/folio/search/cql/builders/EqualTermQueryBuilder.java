package org.folio.search.cql.builders;

import static org.folio.search.utils.SearchUtils.getPathToFulltextPlainValue;
import static org.folio.search.utils.SearchUtils.isEmptyString;
import static org.opensearch.index.query.MultiMatchQueryBuilder.Type.CROSS_FIELDS;
import static org.opensearch.index.query.Operator.AND;
import static org.opensearch.index.query.QueryBuilders.existsQuery;
import static org.opensearch.index.query.QueryBuilders.matchQuery;
import static org.opensearch.index.query.QueryBuilders.multiMatchQuery;

import java.util.List;
import java.util.Set;
import org.folio.search.model.types.ResourceType;
import org.opensearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class EqualTermQueryBuilder extends FulltextQueryBuilder {

  @Override
  public QueryBuilder getQuery(Object term, ResourceType resource, List<String> modifiers, String... fields) {
    return fields.length == 1 && isEmptyString(term)
           ? existsQuery(updatePathForTermQueries(resource, fields[0]))
           : multiMatchQuery(term, fields).operator(AND).type(CROSS_FIELDS);
  }

  @Override
  public QueryBuilder getFulltextQuery(Object term, String fieldName, ResourceType resource, List<String> modifiers) {
    return isEmptyString(term)
           ? existsQuery(getPathToFulltextPlainValue(fieldName))
           : getQuery(term, resource, modifiers, updatePathForFulltextQuery(resource, fieldName));
  }

  @Override
  public QueryBuilder getTermLevelQuery(Object term, String fieldName, ResourceType resource, String fieldIndex) {
    return isEmptyString(term) ? existsQuery(fieldName) : matchQuery(fieldName, term).operator(AND);
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of("=");
  }
}
