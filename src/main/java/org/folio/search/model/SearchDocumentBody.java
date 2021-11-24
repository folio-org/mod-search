package org.folio.search.model;

import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.service.converter.ConversionContext;

/**
 * Contains all required field to perform elasticsearch index operation in mod-search service.
 */
@Data
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
   * @param resourceIdEvent resource id event
   * @return created {@link SearchDocumentBody} object
   */
  public static SearchDocumentBody forResourceIdEvent(ResourceIdEvent resourceIdEvent) {
    return new SearchDocumentBody(resourceIdEvent.getId(), resourceIdEvent.getTenant(),
      getElasticsearchIndexName(resourceIdEvent), null, resourceIdEvent.getAction());
  }

  /**
   * Creates {@link SearchDocumentBody} for passed {@link ResourceIdEvent} resource id event.
   *
   * @param resourceEvent resource id event
   * @return created {@link SearchDocumentBody} object
   */
  public static SearchDocumentBody forDeleteResourceEvent(ResourceEvent resourceEvent) {
    return new SearchDocumentBody(resourceEvent.getId(), resourceEvent.getTenant(),
      getElasticsearchIndexName(resourceEvent), null, DELETE);
  }

  /**
   * Creates {@link SearchDocumentBody} for passed {@link ResourceIdEvent} resource id event.
   *
   * @param ctx - conversion context as {@link ConversionContext} object
   * @param rawJsonBody - raw json body for indexing as {@link String} object
   * @return created {@link SearchDocumentBody} object
   */
  public static SearchDocumentBody forConversionContext(ConversionContext ctx, String rawJsonBody) {
    return new SearchDocumentBody(ctx.getId(), ctx.getTenant(),
      getElasticsearchIndexName(ctx.getResourceDescription().getName(), ctx.getTenant()), rawJsonBody, INDEX);
  }
}
