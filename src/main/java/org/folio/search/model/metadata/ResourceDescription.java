package org.folio.search.model.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.Data;

/**
 * POJO class for specifying a resource description in local json files or dedicated database.
 */
@Data
public class ResourceDescription {

  /**
   * Resource name.
   */
  private String name;

  /**
   * Elasticsearch index name.
   */
  private String index;

  /**
   * Map with field descriptions.
   *
   * The field description should contain json path to the value in event document and elasticsearch
   * representation for this value or reference to the group with set of values.
   */
  private Map<String, FieldDescription> fields;

  /**
   * Map with groups for specific resource.
   *
   * It can be used when several values should be grouped at one place to reduce the number of
   * fields in elasticsearch and play with relevancy and boosting results for specific group.
   */
  private Map<String, PlainFieldDescription> groups;

  /**
   * Map with index fields, that can be used for copy_to functionality of elasticsearch.
   */
  private Map<String, JsonNode> indexMappings;
}
