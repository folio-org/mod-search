package org.folio.search.client;

import java.net.URI;
import org.folio.search.domain.dto.ReindexJob;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient("reindex-client")
public interface ResourceReindexClient {

  /**
   * Submits reindex request by generated reindex {@link URI} object.
   *
   * @param reindexUri - generated reindex uri object
   * @return response body as {@link ReindexJob} object
   */
  @PostMapping
  ReindexJob submitReindex(URI reindexUri);
}
