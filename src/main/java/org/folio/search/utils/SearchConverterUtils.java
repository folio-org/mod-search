package org.folio.search.utils;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchConverterUtils {

  /**
   * Retrieves field value by path. It will extract all field value matching following keys separated by dot. If map
   * value contain list of maps - it will extract all related values from each sub map.
   *
   * @param path path in format 'field1.field2.field3'
   * @param map map to process
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
        if (listValue instanceof String) {
          builder.add((String) listValue);
        }
      }
      return builder.build();
    }
    if (value instanceof String) {
      return Stream.of((String) value);
    }
    return Stream.empty();
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
        .collect(toList());
      return CollectionUtils.isNotEmpty(result) ? result : null;
    }
    return null;
  }
}
