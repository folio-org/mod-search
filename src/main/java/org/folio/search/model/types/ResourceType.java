package org.folio.search.model.types;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ResourceType {

  AUTHORITY("authority"),
  BOUND_WITH("bound_with"),
  CAMPUS("campus"),
  CLASSIFICATION_TYPE("classification-type"),
  HOLDINGS("holdings"),
  INSTANCE("instance"),
  INSTANCE_CONTRIBUTOR("contributor"),
  INSTANCE_CLASSIFICATION("instance_classification"),
  INSTANCE_SUBJECT("instance_subject"),
  INSTITUTION("institution"),
  ITEM("item"),
  LIBRARY("library"),
  LINKED_DATA_HUB("linked-data-hub"),
  LINKED_DATA_INSTANCE("linked-data-instance"),
  LINKED_DATA_WORK("linked-data-work"),
  LOCATION("location"),
  UNKNOWN("unknown");

  @JsonValue
  private final String name;

  ResourceType(String name) {
    this.name = name;
  }

  public static ResourceType byName(String resourceName) {
    for (ResourceType resourceType : values()) {
      if (resourceType.name.equals(resourceName)) {
        return resourceType;
      }
    }
    return UNKNOWN;
  }

  public boolean isLinkedDataResource() {
    return name.startsWith("linked-data");
  }
}
