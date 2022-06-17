package org.folio.search.model.metadata;

import static java.util.Collections.unmodifiableMap;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.fasterxml.jackson.databind.util.StdConverter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This Jackson converter is used to post-process ResourceDescription after it has been deserialized.
 *
 * <p>It does the following actions:</p>
 * <ul>
 * <li>Resolves types for object/plain fields. Type must be defined in {@code fieldTypes} section of the resource
 * description, and then it can be referenced in actual type via $type: [type] property.</li>
 * <li>Builds flattened map of field path and field description pairs.</li>
 * </ul>
 */
public class PostProcessResourceDescriptionConverter extends StdConverter<ResourceDescription, ResourceDescription> {

  @Override
  public ResourceDescription convert(ResourceDescription value) {
    resolveFieldByType(value, value.getFields());
    value.setFlattenFields(getFlattenFields(value));

    return value;
  }

  private static void resolveFieldByType(ResourceDescription desc, Map<String, FieldDescription> fields) {
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

  private static FieldDescription getFieldByType(ResourceDescription desc, String fieldType) {
    if (!desc.getFieldTypes().containsKey(fieldType)) {
      throw new IllegalStateException("No field type found: " + fieldType);
    }

    return desc.getFieldTypes().get(fieldType);
  }

  private static Map<String, PlainFieldDescription> getFlattenFields(ResourceDescription desc) {
    var result = new LinkedHashMap<String, PlainFieldDescription>();
    result.putAll(getFlattenFields(null, desc.getFields()));
    result.putAll(getFlattenFields(null, desc.getSearchFields()));
    return unmodifiableMap(result);
  }

  private static Map<String, PlainFieldDescription> getFlattenFields(
    String path, Map<String, ? extends FieldDescription> fields) {
    var result = new LinkedHashMap<String, PlainFieldDescription>();
    fields.forEach((currentName, desc) -> {
      var currentPath = getFieldPath(path, currentName);

      if (desc instanceof ObjectFieldDescription) {
        result.putAll(getFlattenFields(currentPath, ((ObjectFieldDescription) desc).getProperties()));
      }

      if (desc instanceof PlainFieldDescription) {
        result.put(currentPath, (PlainFieldDescription) desc);
      }
    });

    return unmodifiableMap(result);
  }

  private static String getFieldPath(String parentPath, String currentName) {
    return isBlank(parentPath) ? currentName : parentPath + "." + currentName;
  }
}
