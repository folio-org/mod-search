package org.folio.search.exception;

public class ReindexException extends RuntimeException {

  public ReindexException(String errorMessage) {
    super(errorMessage);
  }
}
