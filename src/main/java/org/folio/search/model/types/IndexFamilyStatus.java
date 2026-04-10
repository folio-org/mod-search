package org.folio.search.model.types;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum IndexFamilyStatus {

  BUILDING("building"),
  STAGED("staged"),
  CUTTING_OVER("cutting_over"),
  FAILED("failed"),
  ACTIVE("active"),
  RETIRING("retiring"),
  RETIRED("retired");

  @JsonValue
  private final String value;

  IndexFamilyStatus(String value) {
    this.value = value;
  }

  public static IndexFamilyStatus fromValue(String value) {
    for (IndexFamilyStatus status : values()) {
      if (status.value.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown IndexFamilyStatus: " + value);
  }
}
