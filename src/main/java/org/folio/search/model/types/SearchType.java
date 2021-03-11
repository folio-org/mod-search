package org.folio.search.model.types;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SearchType {

  /**
   * Provides opportunity to use field values for sorting.
   */
  FACET("facet"),

  /**
   * Provides opportunity to use field values for filtering.
   */
  FILTER("filter");

  /**
   * Json representation of enum.
   */
  @JsonValue
  private final String value;
}
