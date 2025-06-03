package org.folio.search.model.types;

import java.util.Locale;
import lombok.Getter;

@Getter
public enum InventoryRecordType {
  INSTANCE("instance", "http://instance-storage/instances"),
  ITEM("item", "http://item-storage/items"),
  HOLDING("holding", "http://holdings-storage/holdings");

  /**
   * record name.
   */
  private final String recordName;

  /**
   * Request path for the record.
   */
  private final String path;

  InventoryRecordType(String recordName, String path) {
    this.recordName = recordName;
    this.path = path;
  }

  @Override
  public String toString() {
    return name().toLowerCase(Locale.ROOT);
  }
}
