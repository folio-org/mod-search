package org.folio.search.model.rest.response;

import static org.folio.search.model.types.SearchOperationStatus.ERROR;
import static org.folio.search.model.types.SearchOperationStatus.SUCCESS;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FolioIndexResourceResponse extends FolioIndexOperationResponse {

  /**
   * Creates {@link FolioPutMappingResponse} object for positive response from elasticsearch.
   *
   * @return {@link FolioPutMappingResponse} object
   */
  public static FolioIndexResourceResponse success() {
    var putMappingResponse = new FolioIndexResourceResponse();
    putMappingResponse.setStatus(SUCCESS);
    return putMappingResponse;
  }

  /**
   * Creates {@link FolioPutMappingResponse} object for negative response from elasticsearch.
   *
   * @return {@link FolioPutMappingResponse} object
   */
  public static FolioIndexResourceResponse error(String errorMessage) {
    var putMappingResponse = new FolioIndexResourceResponse();
    putMappingResponse.setStatus(ERROR);
    putMappingResponse.setErrorMessage(errorMessage);
    return putMappingResponse;
  }
}
