package org.folio.search.model.types;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SortFieldType {

  /**
   * Sort value is single, no additional settings would be applied in the sort builder.
   */
  SINGLE("single"),

  /**
   * Sort value is collection, sort mode would be applied in the sort builder.
   */
  COLLECTION("collection");

  /**
   * String representation of enum.
   */
  @JsonValue
  private final String value;
}
