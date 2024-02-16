package org.folio.search.cql;

import lombok.RequiredArgsConstructor;
import org.folio.search.service.setter.instance.LccnInstanceProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LccnSearchTermProcessor implements SearchTermProcessor {

  private final LccnInstanceProcessor lccnProcessor;

  @Override
  public String getSearchTerm(String inputTerm) {
    return lccnProcessor.normalizeLccn(inputTerm);
  }
}
