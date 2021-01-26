package org.folio.search.exception;

import lombok.Getter;

@Getter
public class ValidationException extends RuntimeException {
  private final String key;
  private final String value;

  public ValidationException(String message, String key, String value) {
    super(message);

    this.key = key;
    this.value = value;
  }
}
