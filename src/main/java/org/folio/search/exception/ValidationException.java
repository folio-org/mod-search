package org.folio.search.exception;

import lombok.Getter;
import org.folio.search.model.types.ErrorCode;

@Getter
public class ValidationException extends BaseSearchException {

  private final String key;
  private final String value;

  /**
   * Creates {@link ValidationException} object for given message, key and value.
   *
   * @param message - validation error message
   * @param key     - validation key as field or parameter name
   * @param value   - invalid parameter value
   */
  public ValidationException(String message, String key, String value) {
    super(message, ErrorCode.VALIDATION_ERROR);

    this.key = key;
    this.value = value;
  }
}
