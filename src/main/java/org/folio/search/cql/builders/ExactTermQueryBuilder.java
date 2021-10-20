package org.folio.search.cql.builders;

import static org.elasticsearch.index.query.MultiMatchQueryBuilder.Type.PHRASE;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.elasticsearch.index.query.QueryBuilders.scriptQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.folio.search.utils.SearchUtils.EMPTY_ARRAY;
import static org.folio.search.utils.SearchUtils.KEYWORD_FIELD_INDEX;
import static org.folio.search.utils.SearchUtils.getPathToFulltextPlainValue;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.ScriptQueryBuilder;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExactTermQueryBuilder extends FulltextQueryBuilder {

  public static final String SCRIPT_TEMPLATE = "doc['%s'].size() == 0";

  @Override
  public QueryBuilder getQuery(String term, String resource, String... fields) {
    return multiMatchQuery(term, fields).type(PHRASE);
  }

  @Override
  public QueryBuilder getFulltextQuery(String term, String fieldName, String resource) {
    if (term.isEmpty()) {
      return termQuery(getPathToFulltextPlainValue(fieldName), term);
    }

    return EMPTY_ARRAY.equals(term)
      ? getEmptyArrayScriptQuery(getPathToFulltextPlainValue(fieldName))
      : getQuery(term, resource, updatePathForFulltextQuery(resource, fieldName));
  }

  @Override
  public QueryBuilder getTermLevelQuery(String term, String fieldName, String resource, String fieldIndex) {
    return EMPTY_ARRAY.equals(term) && KEYWORD_FIELD_INDEX.equals(fieldIndex)
      ? getEmptyArrayScriptQuery(fieldName)
      : termQuery(fieldName, term);
  }

  private static ScriptQueryBuilder getEmptyArrayScriptQuery(String fieldName) {
    return scriptQuery(new Script(String.format(SCRIPT_TEMPLATE, fieldName)));
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of("==");
  }
}
