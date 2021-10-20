package org.folio.search.model;

import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.model.types.IndexActionType;

/**
 * Contains all required field to perform elasticsearch index operation in mod-search service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class SearchDocumentBody {

  /**
   * Document id.
   */
  private String id;

  /**
   * Document routing.
   */
  private String routing;

  /**
   * Elasticsearch index name.
   */
  private String index;

  /**
   * Document body for elasticsearch index operation.
   */
  private String rawJson;

  /**
   * Elasticsearch action - index or remove.
   */
  private IndexActionType action;

  /**
   * Creates {@link SearchDocumentBody} for passed {@link ResourceIdEvent} resource id event.
   *
   * @param evt resource id event
   * @return created {@link SearchDocumentBody} object
   */
  public static SearchDocumentBody forResourceIdEvent(ResourceIdEvent evt) {
    return new SearchDocumentBody(evt.getId(), evt.getTenant(), getElasticsearchIndexName(evt), null, evt.getAction());
  }

  /**
   * Creates {@link SearchDocumentBody} for passed {@link ResourceIdEvent} resource id event.
   *
   * @param evt resource id event
   * @return created {@link SearchDocumentBody} object
   */
  public static SearchDocumentBody forDeleteResourceEvent(ResourceEventBody evt) {
    return new SearchDocumentBody(evt.getId(), evt.getTenant(), getElasticsearchIndexName(evt), null, DELETE);
  }
}
