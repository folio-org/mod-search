package org.folio.search.cql.searchterm;

import static org.folio.search.utils.SearchUtils.ASTERISKS_SIGN;

import lombok.RequiredArgsConstructor;
import org.folio.search.service.setter.instance.IsbnProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IsbnSearchTermProcessor implements SearchTermProcessor {

  private final IsbnProcessor isbnProcessor;

  @Override
  public String getSearchTerm(String inputTerm) {
    var hasWildcard = inputTerm.endsWith(ASTERISKS_SIGN);
    var termToNormalize = hasWildcard ? inputTerm.substring(0, inputTerm.length() - 1) : inputTerm;
    var normalized = String.join(" ", isbnProcessor.normalizeIsbn(termToNormalize));

    return hasWildcard ? normalized + ASTERISKS_SIGN : normalized;
  }
}
