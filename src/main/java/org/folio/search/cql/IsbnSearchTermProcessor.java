package org.folio.search.cql;

import lombok.RequiredArgsConstructor;
import org.folio.search.service.setter.instance.IsbnProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IsbnSearchTermProcessor implements SearchTermProcessor {

  private final IsbnProcessor isbnProcessor;

  @Override
  public String getSearchTerm(String inputTerm) {
    return String.join(" ", isbnProcessor.normalizeIsbn(inputTerm));
  }
}
