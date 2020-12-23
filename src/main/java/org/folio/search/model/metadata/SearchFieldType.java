package org.folio.search.model.metadata;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

/**
 * Contains description for common local/remote index field settings.
 */
@Data
public class SearchFieldType {

  /**
   * Elasticsearch field mapping for index field type as {@link ObjectNode} object.
   */
  private ObjectNode mapping;
}
