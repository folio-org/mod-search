package org.folio.search.cql.builders;

import static org.opensearch.index.query.MultiMatchQueryBuilder.Type.CROSS_FIELDS;
import static org.opensearch.index.query.Operator.AND;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchQuery;
import static org.opensearch.index.query.QueryBuilders.multiMatchQuery;

import java.util.List;
import java.util.Set;
import org.opensearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class AllTermQueryBuilder extends FulltextQueryBuilder {

  private static final String WHITE_SPACE = "\\s+";

  @Override
  public QueryBuilder getQuery(Object term, String resource, String... fields) {
    if (term instanceof String) {
      var stringTerm = (String) term;
      var terms = stringTerm.split(WHITE_SPACE);

      if (terms.length == 1) {
        return getMultiMatchQuery(terms[0], fields);
      } else {
        return getBoolQuery(terms, fields);
      }
    }

    return getMultiMatchQuery(term, fields);
  }

  @Override
  public QueryBuilder getFulltextQuery(Object term, String fieldName, String resource, List<String> modifiers) {
    return getQuery(term, resource, updatePathForFulltextQuery(resource, fieldName));
  }

  @Override
  public QueryBuilder getTermLevelQuery(Object term, String fieldName, String resource, String fieldIndex) {
    return matchQuery(fieldName, term).operator(AND);
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of("all", "adj");
  }

  private QueryBuilder getMultiMatchQuery(Object term, String... fieldNames) {
    return multiMatchQuery(term, fieldNames).operator(AND).type(CROSS_FIELDS);
  }

  private QueryBuilder getBoolQuery(String[] terms, String... fieldNames) {
    var boolQuery = boolQuery();
    for (var singleTerm : terms) {
      boolQuery.must(getMultiMatchQuery(singleTerm, fieldNames));
    }
    return boolQuery;
  }
}
