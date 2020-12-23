package org.folio.search.model.types;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SearchFieldType {

  /**
   * Provides opportunity to use field values for sorting.
   */
  FACET ("facet"),

  /**
   * Provides opportunity to use field values for faceting.
   */
  SORT ("sort"),

  /**
   * Provides opportunity to use field values as language source for multi-language search.
   */
  LANGUAGE_SOURCE("language_source"),

  /**
   * Specifies that field values are multi-language.
   */
  MULTI_LANGUAGE("multilang"),

  /**
   * Specifies that field contains internal values.
   */
  OBJECT("object");

  /**
   * Json representation of enum.
   */
  @JsonValue
  private final String value;
}
