package org.folio.search.utils;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse.StatusEnum;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchResponseHelper {

  public static final String REQUEST_NOT_ALLOWED_MSG =
    "The request allowed only for central tenant of consortium environment";

  /**
   * Creates positive {@link FolioCreateIndexResponse} object with list of created indices.
   *
   * @param indices list of created indices
   * @return created {@link FolioCreateIndexResponse} object
   */
  public static FolioCreateIndexResponse getSuccessFolioCreateIndexResponse(List<String> indices) {
    var response = new FolioCreateIndexResponse();
    response.setIndices(indices);
    response.setStatus(FolioCreateIndexResponse.StatusEnum.SUCCESS);
    return response;
  }

  /**
   * Creates negative {@link FolioCreateIndexResponse} with error status and list of indices.
   *
   * @param indices list of indices that was not created
   * @return created {@link FolioCreateIndexResponse} object
   */
  public static FolioCreateIndexResponse getErrorFolioCreateIndexResponse(List<String> indices) {
    var response = new FolioCreateIndexResponse();
    response.setIndices(indices);
    response.setErrorMessage("Failed to create indices in elasticsearch");
    response.setStatus(FolioCreateIndexResponse.StatusEnum.ERROR);
    return response;
  }

  /**
   * Creates positive {@link FolioIndexOperationResponse} object.
   *
   * @return created {@link FolioIndexOperationResponse} object
   */
  public static FolioIndexOperationResponse getSuccessIndexOperationResponse() {
    var response = new FolioIndexOperationResponse();
    response.setStatus(StatusEnum.SUCCESS);
    return response;
  }

  /**
   * Creates negative {@link FolioIndexOperationResponse} object with error message.
   *
   * @return created {@link FolioIndexOperationResponse} object
   */
  public static FolioIndexOperationResponse getErrorIndexOperationResponse(String errorMessage) {
    var response = new FolioIndexOperationResponse();
    response.setStatus(StatusEnum.ERROR);
    response.setErrorMessage(errorMessage);
    return response;
  }
}
