package org.folio.search.cql;

import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.elasticsearch.search.suggest.SuggestBuilders.completionSuggestion;

import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.folio.search.model.service.SuggestServiceRequest;
import org.springframework.stereotype.Component;

@Component
public class SuggestQueryConverter {

  public static final String SUGGEST_FIELD = "suggestion";
  public static final String WILDCARD_SUGGEST_FIELD = "wildcardSuggestion";
  public static final String COMPLETION_SUGGEST_NAME = "completion";

  public SearchSourceBuilder convertToWildcardSuggestQuery(SuggestServiceRequest request) {
    return searchSource().query(wildcardQuery(WILDCARD_SUGGEST_FIELD, request.getQuery() + "*"))
      .size(request.getLimit() + 20).from(0).fetchSource(new String[] {WILDCARD_SUGGEST_FIELD}, null);
  }

  public SearchSourceBuilder convert(SuggestServiceRequest r) {
    var suggest = completionSuggestion(SUGGEST_FIELD).prefix(r.getQuery()).size(r.getLimit()).skipDuplicates(true);
    var suggestionQuery = new SuggestBuilder().addSuggestion(COMPLETION_SUGGEST_NAME, suggest);
    return searchSource().suggest(suggestionQuery).size(0).from(0).fetchSource(false);
  }
}
