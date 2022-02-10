package org.folio.search.repository;

import java.util.List;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.index.SearchDocumentBody;

public interface ResourceRepository {

  /**
   * Saves provided list of {@link SearchDocumentBody} objects to elasticsearch.
   *
   * @param esDocumentBodies list wth {@link SearchDocumentBody} object
   */
  FolioIndexOperationResponse indexResources(List<SearchDocumentBody> esDocumentBodies);
}
