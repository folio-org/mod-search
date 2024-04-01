package org.folio.search.cql;

import lombok.RequiredArgsConstructor;
import org.folio.search.utils.SearchUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NaturalIdSearchTermProcessor implements SearchTermProcessor {

  @Override
  public Object getSearchTerm(String inputTerm) {
    return SearchUtils.normalizeNaturalId(inputTerm);
  }
}
