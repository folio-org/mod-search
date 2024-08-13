package org.folio.search.model.types;

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
}
