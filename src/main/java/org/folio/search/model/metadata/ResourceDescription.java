package org.folio.search.model.metadata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 * POJO class for specifying a resource description in local json files or dedicated database.
 */
@Data
@JsonDeserialize(converter = PostProcessResourceDescriptionConverter.class)
public class ResourceDescription {

  /**
   * Resource name.
   */
  private String name;

  /**
   * Name of the parent resource description (can be used if the resource is created from the parent for some purposes).
   */
  private String parent;

  /**
   * Related java class for event body.
   */
  private Class<?> eventBodyJavaClass;

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

  /**
   * Defined reusable types that can be referenced via $type property.
   */
  private Map<String, FieldDescription> fieldTypes = Collections.emptyMap();

  /**
   * Mappings source definition.
   */
  private Map<String, List<String>> mappingsSource;

  /**
   * Resource indexing configuration - pre-processing, resource repository settings etc.
   */
  private ResourceIndexingConfiguration indexingConfiguration;

  /**
   * Map with field description where key is the flattened path.
   */
  @JsonIgnore
  @Setter(AccessLevel.PACKAGE)
  private Map<String, PlainFieldDescription> flattenFields;
}
