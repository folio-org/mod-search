package org.folio.search.model.metadata;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

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
   * Contains list of json path expressions to extract languages values in ISO-639 format.
   */
  private List<String> languageSourcePaths = Collections.emptyList();

  /**
   * Map with field descriptions.
   *
   * <p>The field description should contain json path to the value in event document and elasticsearch representation
   * for this value or reference to the group with set of values.</p>
   */
  private Map<String, FieldDescription> fields;

  /**
   * Map with additional generated fields for specific resource.
   */
  private Map<String, SearchFieldDescriptor> searchFields = Collections.emptyMap();

  /**
   * Map with index fields, that can be used for copy_to functionality of elasticsearch.
   */
  private Map<String, JsonNode> indexMappings;

  @JsonIgnore
  @Setter(value = AccessLevel.NONE)
  private Map<String, PlainFieldDescription> flattenFields;

  public void setFields(Map<String, FieldDescription> fields) {
    if (!Objects.equals(this.fields, fields)) {
      this.fields = fields;
      this.flattenFields = flattenFields(null, new HashMap<>(), fields);
    }
  }

  private Map<String, PlainFieldDescription> flattenFields(String parentPath,
    Map<String, PlainFieldDescription> flattenFields,
    Map<String, FieldDescription> originFields) {

    originFields.forEach((currentName, desc) -> {
      final var currentPath = getFieldPath(parentPath, currentName);

      if (desc instanceof ObjectFieldDescription) {
        flattenFields(currentPath, flattenFields, ((ObjectFieldDescription) desc).getProperties());
      } else if (desc instanceof PlainFieldDescription) {
        flattenFields.put(currentPath, (PlainFieldDescription) desc);
      }
    });

    return flattenFields;
  }

  private String getFieldPath(String parentPath, String currentName) {
    return isBlank(parentPath) ? currentName : parentPath + "." + currentName;
  }
}
