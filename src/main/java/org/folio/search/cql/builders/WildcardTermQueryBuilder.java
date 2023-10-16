package org.folio.search.cql.builders;

import static org.folio.search.cql.CqlTermQueryConverter.WILDCARD_OPERATOR;
import static org.folio.search.utils.SearchUtils.getPathToFulltextPlainValue;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.wildcardQuery;

import java.util.List;
import java.util.Set;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.WildcardQueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class WildcardTermQueryBuilder extends FulltextQueryBuilder {

  @Override
  public QueryBuilder getQuery(Object term, String resource, List<String> modifiers, String... fields) {
    if (fields.length == 1) {
      return getWildcardQuery(term, updatePathForTermQueries(resource, fields[0]));
    }

    var boolQueryBuilder = boolQuery();
    for (var field : fields) {
      boolQueryBuilder.should(getWildcardQuery(term, updatePathForTermQueries(resource, field)));
    }
    return boolQueryBuilder;
  }

  @Override
  public QueryBuilder getFulltextQuery(Object term, String fieldName, String resource, List<String> modifiers) {
    return getWildcardQuery(term, getPathToFulltextPlainValue(fieldName));
  }

  @Override
  public QueryBuilder getTermLevelQuery(Object term, String fieldName, String resource, String fieldIndex) {
    return getWildcardQuery(term, fieldName);
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of(WILDCARD_OPERATOR);
  }

  private static WildcardQueryBuilder getWildcardQuery(Object term, String field) {
    return wildcardQuery(field, String.valueOf(term)).rewrite("constant_score");
  }
}
