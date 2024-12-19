package org.folio.search.model.types;

import lombok.Getter;

@Getter
public enum ReindexRangeStatus {
  SUCCESS("Success"),
  FAIL("Fail");

  private final String value;

  ReindexRangeStatus(String value) {
    this.value = value;
  }

  public static ReindexRangeStatus valueOfNullable(String value) {
    if (value == null) {
      return null;
    }

    for (ReindexRangeStatus b : ReindexRangeStatus.values()) {
      if (b.name().equalsIgnoreCase(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}
