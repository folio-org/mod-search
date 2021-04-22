package org.folio.search.cql;

import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.elasticsearch.search.suggest.SuggestBuilders.completionSuggestion;

import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.completion.CompletionSuggestionBuilder;
import org.folio.search.model.service.SuggestServiceRequest;
import org.springframework.stereotype.Component;

@Component
public class SuggestQueryConverter {

  public static final String SUGGEST_FIELD = "suggest";
  public static final String COMPLETION_SUGGEST_NAME = "completion";

  public SearchSourceBuilder convert(SuggestServiceRequest request) {
    CompletionSuggestionBuilder completionSuggest = completionSuggestion(SUGGEST_FIELD)
      .prefix(request.getQuery())
      .size(request.getLimit())
      .skipDuplicates(true);

    return searchSource().suggest(new SuggestBuilder().addSuggestion(COMPLETION_SUGGEST_NAME, completionSuggest));
  }
}
