package org.folio.search.cql.builders;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.folio.search.cql.CqlTermQueryConverter.WILDCARD_OPERATOR;
import static org.folio.search.utils.SearchUtils.getPathToFulltextPlainValue;

import java.util.Set;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.WildcardQueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class WildcardTermQueryBuilder extends FulltextQueryBuilder {

  @Override
  public QueryBuilder getQuery(String term, String resource, String... fields) {
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
  public QueryBuilder getFulltextQuery(String term, String fieldName, String resource) {
    return getWildcardQuery(term, getPathToFulltextPlainValue(fieldName));
  }

  @Override
  public QueryBuilder getTermLevelQuery(String term, String fieldName, String resource, String fieldIndex) {
    return getWildcardQuery(term, fieldName);
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of(WILDCARD_OPERATOR);
  }

  private static WildcardQueryBuilder getWildcardQuery(String term, String field) {
    return wildcardQuery(field, term).rewrite("constant_score");
  }
}
