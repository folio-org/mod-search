package org.folio.search.model.types;

import lombok.Getter;

@Getter
public enum ResourceType {

  INSTANCE("instance"),
  HOLDINGS("holdings"),
  AUTHORITY("authority"),
  CLASSIFICATION_TYPE("classification-type");

  private final String value;

  ResourceType(String value) {
    this.value = value;
  }
}
