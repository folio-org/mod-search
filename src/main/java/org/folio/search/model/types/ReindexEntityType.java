package org.folio.search.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public enum ReindexEntityType {

  INSTANCE("instance"),
  SUBJECT("subject"),
  CONTRIBUTOR("contributor"),
  CLASSIFICATION("classification"),
  ITEM("item"),
  HOLDING("holding");

  private final String type;

  ReindexEntityType(String type) {
    this.type = type;
  }

  @JsonCreator
  public static ReindexEntityType fromValue(String value) {
    for (ReindexEntityType b : ReindexEntityType.values()) {
      if (b.type.equalsIgnoreCase(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}
