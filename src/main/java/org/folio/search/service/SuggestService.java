package org.folio.search.service;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.folio.search.cql.SuggestQueryConverter.COMPLETION_SUGGEST_NAME;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.suggest.Suggest.Suggestion;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion.Entry;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion.Entry.Option;
import org.folio.search.cql.SuggestQueryConverter;
import org.folio.search.domain.dto.SuggestResult;
import org.folio.search.domain.dto.SuggestTerm;
import org.folio.search.model.service.SuggestServiceRequest;
import org.folio.search.repository.SearchRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SuggestService {

  private final SearchRepository searchRepository;
  private final SuggestQueryConverter suggestQueryConverter;

  /**
   * Provides list of search suggestions as {@link SuggestResult} object.
   *
   * @param request suggest service request
   * @return search suggestions as {@link SuggestResult} object
   */
  public SuggestResult findSuggestions(SuggestServiceRequest request) {
    var searchSource = suggestQueryConverter.convert(request)
      .size(0).from(0).fetchSource(false);

    var searchResponse = searchRepository.search(request, searchSource);
    return mapToSuggestResult(searchResponse);
  }

  private SuggestResult mapToSuggestResult(SearchResponse response) {
    var suggestEntries = getSuggestEntries(response);
    var suggestTerms = getSuggestTerms(suggestEntries);
    return new SuggestResult().totalRecords(suggestTerms.size()).suggests(suggestTerms);
  }

  private static List<Entry> getSuggestEntries(SearchResponse response) {
    return Optional.ofNullable(response)
      .map(SearchResponse::getSuggest)
      .map(suggest -> suggest.<CompletionSuggestion>getSuggestion(COMPLETION_SUGGEST_NAME))
      .map(Suggestion::getEntries)
      .orElse(emptyList());
  }

  private static List<SuggestTerm> getSuggestTerms(List<Entry> suggestEntries) {
    return suggestEntries.stream()
      .map(Entry::getOptions)
      .flatMap(Collection::stream)
      .map(SuggestService::mapToSuggestTerm)
      .collect(toList());
  }

  private static SuggestTerm mapToSuggestTerm(Option option) {
    return new SuggestTerm()
      .term(option.getText().string())
      .id(option.getHit().getId());
  }
}
