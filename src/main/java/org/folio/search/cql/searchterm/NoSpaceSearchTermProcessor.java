package org.folio.search.cql.searchterm;

import lombok.RequiredArgsConstructor;
import org.folio.search.service.lccn.StringNormalizer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NoSpaceSearchTermProcessor implements SearchTermProcessor {

  private final StringNormalizer stringNormalizer;

  @Override
  public String getSearchTerm(String inputTerm) {
    return stringNormalizer.apply(inputTerm)
      .orElse(null);
  }
}
