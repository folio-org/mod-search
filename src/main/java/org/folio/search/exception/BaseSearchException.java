package org.folio.search.exception;

import lombok.Getter;
import org.folio.search.model.types.ErrorCode;

/**
 * Abstract class that should be used for all exception, produced in mod-search service.
 */
public abstract class BaseSearchException extends RuntimeException {

  @Getter private final ErrorCode errorCode;

  /**
   * Creates exception instance from given message and error code.
   *
   * @param message exception message as {@link String} object
   * @param errorCode exception error code as {@link ErrorCode} object
   */
  public BaseSearchException(String message, ErrorCode errorCode) {
    super(message);
    this.errorCode = errorCode;
  }

  /**
   * Creates exception instance from given message.
   *
   * @param message exception message as{@link String} object
   * @param cause exception cause as {@link Throwable} object
   * @param errorCode exception error code as {@link ErrorCode} object
   */
  public BaseSearchException(String message, Throwable cause, ErrorCode errorCode) {
    super(message, cause);
    this.errorCode = errorCode;
  }
}
