package org.folio.search.exception;

public class ReindexException extends RuntimeException {

  public ReindexException(String errorMessage) {
    super(errorMessage);
  }

  public ReindexException(String errorMessage, Throwable cause) {
    super(errorMessage, cause);
  }
}
