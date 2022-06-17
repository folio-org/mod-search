package org.folio.search.exception;

/**
 * Thrown to indicate an error during loading or processing resource descriptions from local json file or dedicated
 * database.
 */
public class ResourceDescriptionException extends RuntimeException {

  /**
   * Creates exception instance from given message.
   *
   * @param message exception message as {@link String} object
   */
  public ResourceDescriptionException(String message) {
    super(message);
  }

  /**
   * Creates exception instance from given message.
   *
   * @param message   exception message as{@link String} object
   * @param throwable exception cause as {@link Throwable} object
   */
  public ResourceDescriptionException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
