package org.folio.search.model.metadata;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.fasterxml.jackson.databind.util.StdConverter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This Jackson converter is used to post-process ResourceDescription after it has been deserialized.
 * It does following actions:
 * * Resolves types for object/plain fields. Type must be defined in {@code fieldTypes} section of the
 * resource description and than it can be referenced in actual type via $type: [type] property.
 * * Builds flattened map of field path and field description pairs.
 */
public class PostProcessResourceDescriptionConverter extends StdConverter<ResourceDescription, ResourceDescription> {
  @Override
  public ResourceDescription convert(ResourceDescription value) {
    resolveFieldByType(value, value.getFields());
    value.setFlattenFields(flattenFields(value));

    return value;
  }

  private void resolveFieldByType(ResourceDescription desc, Map<String, FieldDescription> fields) {
    for (var entry : fields.entrySet()) {
      var fieldType = entry.getValue().getFieldType();
      if (isNotBlank(fieldType)) {
        entry.setValue(getFieldByType(desc, fieldType));
      }

      // Resolve nested properties recursively
      var field = entry.getValue();
      if (field instanceof ObjectFieldDescription) {
        resolveFieldByType(desc, ((ObjectFieldDescription) field).getProperties());
      }
    }
  }

  private FieldDescription getFieldByType(ResourceDescription desc, String fieldType) {
    if (!desc.getFieldTypes().containsKey(fieldType)) {
      throw new IllegalStateException("No field type found: " + fieldType);
    }

    return desc.getFieldTypes().get(fieldType);
  }

  private Map<String, PlainFieldDescription> flattenFields(ResourceDescription desc) {
    return flattenFields(null, new LinkedHashMap<>(), desc.getFields());
  }

  private Map<String, PlainFieldDescription> flattenFields(
    String parentPath, Map<String, PlainFieldDescription> flattenFields,
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
