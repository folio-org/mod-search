package org.folio.search.exception;

import lombok.Getter;
import org.folio.search.model.types.ErrorCode;

@Getter
public class RequestValidationException extends BaseSearchException {

  private final String key;
  private final String value;

  /**
   * Creates {@link RequestValidationException} object for given message, key and value.
   *
   * @param message - validation error message
   * @param key - validation key as field or parameter name
   * @param value - invalid parameter value
   */
  public RequestValidationException(String message, String key, String value) {
    super(message, ErrorCode.VALIDATION_ERROR);

    this.key = key;
    this.value = value;
  }
}
