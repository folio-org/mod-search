package org.folio.search.exception;

import static org.folio.search.model.types.ErrorCode.INTEGRATION_ERROR;

/**
 * Handles exceptional cases of module integration with other Folio modules.
 */
public class FolioIntegrationException extends BaseSearchException {

  /**
   * Initialize exception with provided message and error code.
   *
   * @param message exception message
   */
  public FolioIntegrationException(String message) {
    super(message, INTEGRATION_ERROR);
  }

  /**
   * Initialize exception with provided message and error code.
   *
   * @param message exception message
   * @param cause   cause Exception
   */
  public FolioIntegrationException(String message, Throwable cause) {
    super(message, cause, INTEGRATION_ERROR);
  }
}
