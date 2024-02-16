package org.folio.search.cql;

import lombok.RequiredArgsConstructor;
import org.folio.search.utils.SearchUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LccnSearchTermProcessor implements SearchTermProcessor {

  @Override
  public String getSearchTerm(String inputTerm) {
    return SearchUtils.normalizeLccn(inputTerm);
  }
}
