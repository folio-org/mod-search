package org.folio.search.cql.builders;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.folio.search.cql.CqlTermQueryConverter.WILDCARD_OPERATOR;
import static org.folio.search.utils.SearchUtils.getPathToPlainMultilangValue;
import static org.folio.search.utils.SearchUtils.updatePathForTermQueries;

import java.util.Set;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.WildcardQueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class WildcardTermQueryBuilder implements TermQueryBuilder {

  @Override
  public QueryBuilder getQuery(String term, String[] fields) {
    if (fields.length == 1) {
      return getWildcardQuery(term, updatePathForTermQueries(fields[0]));
    }

    var boolQueryBuilder = boolQuery();
    for (var field : fields) {
      boolQueryBuilder.should(getWildcardQuery(term, updatePathForTermQueries(field)));
    }
    return boolQueryBuilder;
  }

  @Override
  public QueryBuilder getMultilangQuery(String term, String fieldName) {
    return getWildcardQuery(term, getPathToPlainMultilangValue(fieldName));
  }

  @Override
  public QueryBuilder getTermLevelQuery(String term, String fieldName) {
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
