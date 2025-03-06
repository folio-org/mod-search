package org.folio.search.cql.searchterm;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.search.utils.SearchUtils.ASTERISKS_SIGN;

import lombok.RequiredArgsConstructor;
import org.folio.search.utils.SearchUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClassificationNumberSearchTermProcessor implements SearchTermProcessor {

  @Override
  public String getSearchTerm(String inputTerm) {
    boolean hasLeadingWildcard = inputTerm.startsWith(ASTERISKS_SIGN);
    boolean hasTrailingWildcard = inputTerm.endsWith(ASTERISKS_SIGN);
    String normalizedTerm = SearchUtils.normalizeToAlphaNumeric(inputTerm);
    if (isBlank(normalizedTerm)) {
      return ASTERISKS_SIGN;
    }
    return (hasLeadingWildcard ? ASTERISKS_SIGN : EMPTY)
      + normalizedTerm
      + (hasTrailingWildcard ? ASTERISKS_SIGN : EMPTY);
  }
}
