package org.folio.search.model.rest.response;

import lombok.Data;
import org.folio.search.model.types.SearchOperationStatus;

@Data
public class FolioIndexOperationResponse {

  /**
   * Error message with the reason why index was failed to create.
   */
  private String errorMessage;

  /**
   * Index creation status - success or error.
   */
  private SearchOperationStatus status;
}
