package org.folio.search.repository;

import java.util.List;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.ResourceType;

public interface ResourceRepository {

  /**
   * Saves provided list of {@link SearchDocumentBody} objects to elasticsearch.
   *
   * @param esDocumentBodies list wth {@link SearchDocumentBody} object
   */
  FolioIndexOperationResponse indexResources(List<SearchDocumentBody> esDocumentBodies);

  default FolioIndexOperationResponse deleteResourceByTenantId(ResourceType resource, String tenantId) {
    throw new UnsupportedOperationException("Not implemented for repository");
  }
}
