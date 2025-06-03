package org.folio.search.model.index;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.model.types.IndexingDataFormat;
import org.opensearch.core.common.bytes.BytesReference;

/**
 * Contains all required field to perform elasticsearch index operation in mod-search service.
 */
@Data
@AllArgsConstructor(staticName = "of")
public class SearchDocumentBody {

  /**
   * Document body for elasticsearch index operation.
   */
  private BytesReference documentBody;

  /**
   * Document body format for elasticsearch index operation.
   */
  private IndexingDataFormat dataFormat;

  /**
   * Corresponding resource event value, used for delete operations.
   */
  private ResourceEvent resourceEvent;

  /**
   * Elasticsearch action - index or remove.
   */
  private IndexActionType action;

  /**
   * Returns resource event id.
   *
   * @return resource event id as {@link String} object.
   */
  public String getId() {
    return resourceEvent.getId();
  }

  /**
   * Returns event tenant.
   *
   * @return tenant from resource event as {@link String} object.
   */
  public String getTenant() {
    return resourceEvent.getTenant();
  }

  /**
   * Returns resource event name.
   *
   * @return resource name from resource event as {@link String} object.
   */
  public String getResource() {
    return resourceEvent.getResourceName();
  }
}
