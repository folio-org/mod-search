package org.folio.search.exception;

import lombok.Getter;
import org.folio.search.model.types.ErrorCode;

@Getter
public class ValidationException extends BaseSearchException {

  private final String key;
  private final String value;

  public ValidationException(String message, String key, String value) {
    super(message, ErrorCode.VALIDATION_ERROR);

    this.key = key;
    this.value = value;
  }
}
