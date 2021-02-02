package org.folio.search.exception;

/**
 * Thrown to indicate a service error that was occurred at operations to search engine.
 */
public class SearchOperationException extends RuntimeException {

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
