package org.folio.search.model.metadata;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
