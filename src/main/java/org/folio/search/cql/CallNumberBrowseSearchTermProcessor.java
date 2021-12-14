package org.folio.search.cql;

import lombok.RequiredArgsConstructor;
import org.folio.search.service.setter.instance.CallNumberProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallNumberBrowseSearchTermProcessor implements SearchTermProcessor {

  private final CallNumberProcessor callNumberProcessor;

  @Override
  public Long getSearchTerm(String inputTerm) {
    return callNumberProcessor.getCallNumberAsLong(inputTerm);
  }
}
