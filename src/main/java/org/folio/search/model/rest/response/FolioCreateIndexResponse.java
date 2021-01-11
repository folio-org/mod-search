package org.folio.search.model.rest.response;

import static lombok.AccessLevel.PRIVATE;
import static org.folio.search.model.types.SearchOperationStatus.SUCCESS;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.folio.search.model.types.SearchOperationStatus;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor(access = PRIVATE)
public class FolioCreateIndexResponse extends FolioIndexOperationResponse {

  /**
   * Error message with the reason why index was failed to create.
   */
  private String errorMessage;

  /**
   * List of created elasticsearch indices.
   */
  private List<String> indices;

  /**
   * Index creation status - success or error.
   */
  private SearchOperationStatus status;

  /**
   * Creates {@link FolioCreateIndexResponse} object for positive response from elasticsearch.
   *
   * @param indices list of index names
   * @return {@link FolioCreateIndexResponse} object
   */
  public static FolioCreateIndexResponse success(List<String> indices) {
    return new FolioCreateIndexResponse(null, indices, SUCCESS);
  }

  /**
   * Creates {@link FolioCreateIndexResponse} object for negative response from elasticsearch.
   *
   * @param errorMessage error message
   * @param indices list of index names
   * @return {@link FolioCreateIndexResponse} object
   */
  public static FolioCreateIndexResponse error(String errorMessage, List<String> indices) {
    return new FolioCreateIndexResponse(errorMessage, indices, SUCCESS);
  }
}
