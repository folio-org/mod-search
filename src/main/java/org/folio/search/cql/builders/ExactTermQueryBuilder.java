package org.folio.search.cql.builders;

import static org.elasticsearch.index.query.MultiMatchQueryBuilder.Type.PHRASE;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.folio.search.utils.SearchUtils.getPathForMultilangField;

import java.util.Set;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class ExactTermQueryBuilder implements TermQueryBuilder {

  @Override
  public QueryBuilder getQuery(String term, String... fields) {
    return multiMatchQuery(term, fields).type(PHRASE);
  }

  @Override
  public QueryBuilder getMultilangQuery(String term, String fieldName) {
    return getQuery(term, getPathForMultilangField(fieldName));
  }

  @Override
  public QueryBuilder getTermLevelQuery(String term, String fieldName) {
    return termQuery(fieldName, term);
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of("==");
  }
}
