package org.folio.search.cql;

import lombok.RequiredArgsConstructor;
import org.folio.search.service.setter.instance.OclcProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OclcSearchTermProcessor implements SearchTermProcessor {

  private final OclcProcessor oclcProcessor;

  @Override
  public String getSearchTerm(String inputTerm) {
    return oclcProcessor.normalizeOclc(inputTerm);
  }
}
