package org.folio.search.exception;

import lombok.Getter;
import org.folio.search.model.types.ErrorCode;

/**
 * Thrown to indicate a service error that was occurred at operations to search engine.
 */
public class SearchOperationException extends RuntimeException {

  @Getter private final ErrorCode errorCode = ErrorCode.ELASTICSEARCH_ERROR;

  /**
   * Creates exception instance from given message.
   *
   * @param message exception message as {@link String} object
   */
  public SearchOperationException(String message) {
    super(message);
  }

  /**
   * Creates exception instance from given message.
   *
   * @param message exception message as{@link String} object
   * @param throwable exception cause as {@link Throwable} object
   */
  public SearchOperationException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
