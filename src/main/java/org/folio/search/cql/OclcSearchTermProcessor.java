package org.folio.search.cql;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import org.folio.search.service.setter.instance.OclcProcessor;

@Component
@RequiredArgsConstructor
public class OclcSearchTermProcessor implements SearchTermProcessor {

  private final OclcProcessor oclcProcessor;

  @Override
  public String getSearchTerm(String inputTerm) {
    return oclcProcessor.normalizeOclc(inputTerm);
  }
}
