package org.folio.search.utils;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.folio.search.utils.CollectionUtils.nullIfEmpty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;

/**
 * Utility class that walks a resource field-description tree and converts a raw record map
 * into a flat map suitable for indexing into OpenSearch. Handles plain fields, multilang
 * expansion, object fields (including repeatable/list values), and special cases like
 * tenant fields and non-indexed fields.
 */
@UtilityClass
public class ResourceFieldMapper {

  /**
   * Converts a raw data map using the provided resource field descriptions and context.
   *
   * @param data       raw record data as a map
   * @param fields     field description tree from the resource description
   * @param languages  list of supported languages for multilang expansion
   * @param tenantId   tenant identifier for tenant fields
   * @return converted map suitable for indexing, or null if the result is empty
   */
  public static Map<String, Object> convertMapUsingResourceFields(
    Map<String, Object> data, Map<String, FieldDescription> fields,
    List<String> languages, String tenantId) {
    var resultMap = new LinkedHashMap<String, Object>();
    fields.entrySet().forEach(entry -> resultMap.putAll(getFieldValue(data, entry, languages, tenantId)));
    return nullIfEmpty(resultMap);
  }

  static Map<String, Object> getFieldValue(
    Map<String, Object> data, Entry<String, FieldDescription> descEntry,
    List<String> languages, String tenantId) {
    var fieldDescription = descEntry.getValue();
    if (fieldDescription instanceof PlainFieldDescription) {
      return getPlainFieldValue(data, descEntry, languages, tenantId);
    }

    var objectFieldDescription = (ObjectFieldDescription) fieldDescription;
    var fieldName = descEntry.getKey();
    var objectMapValue = data.get(fieldName);
    var value = getObjectFieldValue(objectMapValue, objectFieldDescription.getProperties(), languages, tenantId);
    return value != null ? Map.of(fieldName, value) : emptyMap();
  }

  static Map<String, Object> getPlainFieldValue(Map<String, Object> fieldData,
                                                 Entry<String, FieldDescription> fieldEntry,
                                                 List<String> languages, String tenantId) {
    var fieldName = fieldEntry.getKey();
    var desc = (PlainFieldDescription) fieldEntry.getValue();
    if (desc.isNotIndexed()) {
      return emptyMap();
    }

    if (desc.isTenantField()) {
      return singletonMap(fieldName, tenantId);
    }

    var plainFieldValue = MapUtils.getObject(fieldData, fieldName, desc.getDefaultValue());
    if (plainFieldValue == null) {
      return emptyMap();
    }

    return SearchUtils.getPlainFieldValue(desc, fieldName, plainFieldValue, languages);
  }

  @SuppressWarnings("unchecked")
  static Object getObjectFieldValue(
    Object value, Map<String, FieldDescription> subfields,
    List<String> languages, String tenantId) {
    if (value instanceof Map) {
      return convertMapUsingResourceFields((Map<String, Object>) value, subfields, languages, tenantId);
    }

    if (value instanceof List) {
      return ((List<Object>) value).stream()
        .map(listValue -> getObjectFieldValue(listValue, subfields, languages, tenantId))
        .filter(Objects::nonNull)
        .toList();
    }

    return null;
  }
}
