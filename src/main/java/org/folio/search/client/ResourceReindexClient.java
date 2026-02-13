package org.folio.search.client;

import java.net.URI;
import org.folio.search.domain.dto.ReindexJob;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface ResourceReindexClient {

  /**
   * Submits reindex request by generated reindex {@link URI} object.
   *
   * @param reindexUri - generated reindex uri object
   * @return response body as {@link ReindexJob} object
   */
  @PostExchange
  ReindexJob submitReindex(URI reindexUri);
}
