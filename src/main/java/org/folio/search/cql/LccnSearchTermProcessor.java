package org.folio.search.cql;

import lombok.RequiredArgsConstructor;
import org.folio.search.service.lccn.LccnNormalizer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LccnSearchTermProcessor implements SearchTermProcessor {

  private final LccnNormalizer lccnNormalizer;

  @Override
  public String getSearchTerm(String inputTerm) {
    return lccnNormalizer.apply(inputTerm)
      .orElse(null);
  }
}
