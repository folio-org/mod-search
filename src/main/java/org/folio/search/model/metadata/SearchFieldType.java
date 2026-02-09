package org.folio.search.model.metadata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.node.ObjectNode;

/**
 * Contains description for common local/remote index field settings.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class SearchFieldType {

  /**
   * Elasticsearch field mapping for index field type as {@link ObjectNode} object.
   */
  private ObjectNode mapping;
}
