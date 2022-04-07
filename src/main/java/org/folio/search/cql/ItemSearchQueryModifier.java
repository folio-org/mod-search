package org.folio.search.cql;

import org.springframework.stereotype.Component;

@Component
public class ItemSearchQueryModifier implements SearchQueryModifier {

  @Override
  public String modify(String inputQuery) {
    return inputQuery.replace("item.", "items.");
  }
}
