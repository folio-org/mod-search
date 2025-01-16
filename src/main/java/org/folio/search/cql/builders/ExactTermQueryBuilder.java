package org.folio.search.cql.builders;

import static org.folio.search.utils.SearchUtils.EMPTY_ARRAY;
import static org.folio.search.utils.SearchUtils.KEYWORD_FIELD_INDEX;
import static org.folio.search.utils.SearchUtils.getPathToFulltextPlainValue;
import static org.opensearch.index.query.MultiMatchQueryBuilder.Type.PHRASE;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.index.query.QueryBuilders.multiMatchQuery;
import static org.opensearch.index.query.QueryBuilders.scriptQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.index.query.QueryBuilders.termsQuery;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.folio.search.model.types.ResourceType;
import org.folio.search.utils.SearchUtils;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.ScriptQueryBuilder;
import org.opensearch.script.Script;
import org.springframework.stereotype.Component;

@Component
public class ExactTermQueryBuilder extends FulltextQueryBuilder {

  public static final String SCRIPT_TEMPLATE = "doc['%s'].size() == 0";

  private static final String STRING_MODIFIER = "string";

  @Override
  public QueryBuilder getQuery(Object term, ResourceType resource, List<String> modifiers, String... fields) {
    if (modifiers.contains(STRING_MODIFIER)) {
      fields = getUpdatedFields(fields);
    }
    return multiMatchQuery(term, fields).type(PHRASE);
  }

  @Override
  public QueryBuilder getFulltextQuery(Object term, String fieldName, ResourceType resource, List<String> modifiers) {
    if (SearchUtils.isEmptyString(term) || modifiers.contains(STRING_MODIFIER)) {
      return termQuery(getPathToFulltextPlainValue(fieldName), term);
    }

    return EMPTY_ARRAY.equals(term)
           ? getEmptyArrayScriptQuery(getPathToFulltextPlainValue(fieldName))
           : getQuery(term, resource, modifiers, updatePathForFulltextQuery(resource, fieldName));
  }

  @Override
  public QueryBuilder getTermLevelQuery(Object term, String fieldName, ResourceType resource, String fieldIndex) {
    if (term instanceof String[] termArray) {
      return getTermArrayQuery(termArray, fieldName);
    }
    return getSingleTermQuery(term, fieldName, fieldIndex);
  }

  @Override
  public Set<String> getSupportedComparators() {
    return Set.of("==");
  }

  private QueryBuilder getTermArrayQuery(String[] termArray, String fieldName) {
    if (termArray.length == 0) {
      return matchAllQuery();
    }
    return termArray.length == 1 ? termQuery(fieldName, termArray[0]) : termsQuery(fieldName, termArray);
  }

  private QueryBuilder getSingleTermQuery(Object term, String fieldName, String fieldIndex) {
    if (EMPTY_ARRAY.equals(term) && KEYWORD_FIELD_INDEX.equals(fieldIndex)) {
      return getEmptyArrayScriptQuery(fieldName);
    }
    return termQuery(fieldName, term);
  }

  private static ScriptQueryBuilder getEmptyArrayScriptQuery(String fieldName) {
    return scriptQuery(new Script(String.format(SCRIPT_TEMPLATE, fieldName)));
  }

  private static String[] getUpdatedFields(String[] fieldsList) {
    return Arrays.stream(fieldsList)
      .map(SearchUtils::updatePathForTermQueries)
      .toArray(String[]::new);
  }
}
