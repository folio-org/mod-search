package org.folio.search.model.types;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum InventoryRecordType {
  INSTANCE("instance"),
  ITEM("item"),
  HOLDING("holding");

  private final String value;
}
