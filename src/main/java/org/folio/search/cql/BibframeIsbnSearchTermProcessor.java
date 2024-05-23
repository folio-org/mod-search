package org.folio.search.cql;

import lombok.RequiredArgsConstructor;
import org.folio.search.service.setter.bibframe.BibframeIsbnProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BibframeIsbnSearchTermProcessor implements SearchTermProcessor {

  private final BibframeIsbnProcessor isbnProcessor;

  @Override
  public String getSearchTerm(String inputTerm) {
    return String.join(" ", isbnProcessor.normalizeIsbn(inputTerm));
  }
}
