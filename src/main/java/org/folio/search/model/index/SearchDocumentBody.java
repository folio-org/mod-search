package org.folio.search.model.index;

import static org.folio.search.utils.SearchUtils.getIndexName;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.types.IndexActionType;

/**
 * Contains all required field to perform elasticsearch index operation in mod-search service.
 */
@Data
@AllArgsConstructor(staticName = "of")
public class SearchDocumentBody {

  /**
   * Document body for elasticsearch index operation.
   */
  private String rawJson;

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
   * Returns search document body routing.
   *
   * @return search document routing as {@link String} object.
   */
  public String getRouting() {
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

  /**
   * Returns search document body index name.
   *
   * @return Elasticsearch index name as {@link String} object.
   */
  public String getIndex() {
    return getIndexName(resourceEvent);
  }
}
