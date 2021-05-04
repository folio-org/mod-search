package org.folio.search.model.types;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IndexActionType {

  /**
   * Elasticsearch should provide index operation for that action.
   */
  INDEX("index"),

  /**
   * Elasticsearch should provide delete operation for that action.
   */
  DELETE("delete");

  @JsonValue
  private final String value;
}
