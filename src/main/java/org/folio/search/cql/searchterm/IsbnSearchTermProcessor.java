package org.folio.search.cql.searchterm;

import static org.folio.search.utils.SearchUtils.ASTERISKS_SIGN;

import lombok.RequiredArgsConstructor;
import org.folio.search.service.setter.instance.IsbnProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IsbnSearchTermProcessor implements SearchTermProcessor {

  private final IsbnProcessor isbnProcessor;

  /**
   * Uses IsbnProcessor for normalization. Uses only the first search term returned by IsbnProcessor, if any.
   * Using only first term ensures we can find isbn by exact match.
   * Preserves asterisk at the end of search term, so it doesn't get removed by normalization.
   *
   * @return normalized ISBN search term.
   * */
  @Override
  public String getSearchTerm(String inputTerm) {
    var hasWildcard = inputTerm.endsWith(ASTERISKS_SIGN);
    var termToNormalize = hasWildcard ? inputTerm.substring(0, inputTerm.length() - 1) : inputTerm;
    var normalizedList = isbnProcessor.normalizeIsbn(termToNormalize);
    var normalized = normalizedList.isEmpty() ? termToNormalize : normalizedList.getFirst();

    return hasWildcard ? normalized + ASTERISKS_SIGN : normalized;
  }
}
