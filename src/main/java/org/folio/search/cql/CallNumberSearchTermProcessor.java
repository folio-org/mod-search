package org.folio.search.cql;

import static org.folio.search.cql.CqlSearchQueryConverter.ASTERISKS_SIGN;
import static org.folio.search.utils.SearchUtils.getNormalizedCallNumber;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallNumberSearchTermProcessor implements SearchTermProcessor {

  @Override
  public String getSearchTerm(String inputTerm) {
    return getNormalizedCallNumber(inputTerm) + ASTERISKS_SIGN;
  }
}
