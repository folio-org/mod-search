package org.folio.search.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
