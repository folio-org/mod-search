package org.folio.search.model.types;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FieldType {

  /**
   * Provides opportunity to use field values for sorting.
   */
  PLAIN("plain"),

  /**
   * Provides opportunity to use field values for faceting.
   */
  OBJECT("object");

  /**
   * Json representation of enum.
   */
  @JsonValue
  private final String value;
}
