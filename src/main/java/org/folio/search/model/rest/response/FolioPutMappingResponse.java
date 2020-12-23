package org.folio.search.model.rest.response;

import static lombok.AccessLevel.PRIVATE;
import static org.folio.search.model.types.SearchOperationStatus.ERROR;
import static org.folio.search.model.types.SearchOperationStatus.SUCCESS;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor(access = PRIVATE)
public class FolioPutMappingResponse extends FolioIndexOperationResponse {

  /**
   * Creates {@link FolioPutMappingResponse} object for positive response from elasticsearch.
   *
   * @return {@link FolioPutMappingResponse} object
   */
  public static FolioPutMappingResponse success() {
    var putMappingResponse = new FolioPutMappingResponse();
    putMappingResponse.setStatus(SUCCESS);
    return putMappingResponse;
  }

  /**
   * Creates {@link FolioPutMappingResponse} object for negative response from elasticsearch.
   *
   * @return {@link FolioPutMappingResponse} object
   */
  public static FolioPutMappingResponse error(String errorMessage) {
    var putMappingResponse = new FolioPutMappingResponse();
    putMappingResponse.setStatus(ERROR);
    putMappingResponse.setErrorMessage(errorMessage);
    return putMappingResponse;
  }
}
