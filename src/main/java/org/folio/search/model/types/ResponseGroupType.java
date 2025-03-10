package org.folio.search.model.types;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResponseGroupType {

  /**
   * General response group type to return field for search endpoints.
   */
  SEARCH("search"),

  /**
   * General response group type to return field for browse endpoints.
   */
  BROWSE("browse");

  @JsonValue
  private final String value;
}
