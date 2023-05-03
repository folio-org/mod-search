package org.folio.search.cql;

import static org.folio.search.utils.CallNumberUtils.normalizeCallNumberComponents;
import static org.folio.search.utils.SearchUtils.ASTERISKS_SIGN;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallNumberSearchTermProcessor implements SearchTermProcessor {

  @Override
  public String getSearchTerm(String inputTerm) {
    return normalizeCallNumberComponents(inputTerm) + ASTERISKS_SIGN;
  }
}
