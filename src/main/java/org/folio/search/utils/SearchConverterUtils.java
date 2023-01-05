package org.folio.search.utils;

import static java.util.Collections.emptyMap;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.utils.SearchUtils.ID_FIELD;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchConverterUtils {

  /**
   * Retrieves field value by path. It will extract all field value matching following keys separated by dot. If map
   * value contain list of maps - it will extract all related values from each sub map.
   *
   * @param path path in format 'field1.field2.field3'
   * @param map  map to process
   * @return field value by path.
   */
  public static Object getMapValueByPath(String path, Map<String, Object> map) {
    if (MapUtils.isEmpty(map)) {
      return null;
    }
    var pathToProcess = path.startsWith("$.") ? path.substring(2) : path;
    var values = pathToProcess.split("\\.");
    Object currentValue = map;
    for (String pathValue : values) {
      currentValue = getFieldValueByPath(pathValue, currentValue);
      if (currentValue == null) {
        break;
      }
    }
    return currentValue;
  }

  /**
   * Returns string values from {@link Object} value.
   *
   * <p>
   * If value instance of String, it will return it as {@link Stream} of single value, else if value instance of {@link
   * List} - this method will return all {@link String} values of this list
   * </p>
   *
   * @param value value to process as generic {@link Object}
   * @return {@link Stream} with {@link String} values.
   */
  @SuppressWarnings("unchecked")
  public static Stream<String> getStringStreamFromValue(Object value) {
    if (value instanceof List) {
      var builder = Stream.<String>builder();
      for (Object listValue : (List<Object>) value) {
        if (listValue instanceof String string) {
          builder.add(string);
        }
      }
      return builder.build();
    }
    if (value instanceof String string) {
      return Stream.of(string);
    }
    return Stream.empty();
  }

  /**
   * Returns event payload from {@link ResourceEvent} object.
   *
   * @param event - resource event body to analyze
   * @return event payload as {@link Map} object.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> getEventPayload(ResourceEvent event) {
    return event.getNew() != null ? (Map<String, Object>) event.getNew() : (Map<String, Object>) event.getOld();
  }

  /**
   * Returns fields for latest version of {@link ResourceEvent} object.
   *
   * @param resourceEvent - resource event body to analyze
   * @return event payload as {@link Map} object.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> getNewAsMap(ResourceEvent resourceEvent) {
    return resourceEvent.getNew() != null ? (Map<String, Object>) resourceEvent.getNew() : emptyMap();
  }

  /**
   * Returns fields for previous version of {@link ResourceEvent} object.
   *
   * @param resourceEvent - resource event body to analyze
   * @return event payload as {@link Map} object.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> getOldAsMap(ResourceEvent resourceEvent) {
    return resourceEvent.getOld() != null ? (Map<String, Object>) resourceEvent.getOld() : emptyMap();
  }

  /**
   * Returns resource event id from {@link ResourceEvent} object.
   *
   * @param event - resource event body to analyze
   * @return event id as {@link String} object
   */
  public static String getResourceEventId(ResourceEvent event) {
    return getString(getEventPayload(event), ID_FIELD);
  }

  /**
   * Returns resource event id from event payload {@link Map} object.
   *
   * @param eventPayload - resource event body to analyze
   * @return event id as {@link String} object
   */
  public static String getResourceEventId(Map<String, Object> eventPayload) {
    return getString(eventPayload, ID_FIELD);
  }

  /**
   * Copies entity fields from source to target using given list of fields.
   *
   * @param source - source resource event body as {@link Map} object
   * @param target - target resource event body as {@link Map} object
   * @param fields - field names to copy from source to target as {@link List} object
   */
  public static void copyEntityFields(Map<String, Object> source, Map<String, Object> target, List<String> fields) {
    for (var field : fields) {
      if (source.containsKey(field)) {
        target.put(field, source.get(field));
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Object getFieldValueByPath(String pathValue, Object value) {
    if (value instanceof Map) {
      return ((Map<String, Object>) value).get(pathValue);
    }
    if (value instanceof List) {
      var result = ((List<Object>) value).stream()
        .map(listValue -> getFieldValueByPath(pathValue, listValue))
        .filter(Objects::nonNull)
        .toList();
      return CollectionUtils.isNotEmpty(result) ? result : null;
    }
    return null;
  }
}
