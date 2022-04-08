package org.folio.search.cql;

import org.springframework.stereotype.Component;

@Component
public class ItemSearchFieldModifier implements SearchFieldModifier {

  @Override
  public String modify(String inputField) {
    return inputField.replace("item.", "items.");
  }
}
