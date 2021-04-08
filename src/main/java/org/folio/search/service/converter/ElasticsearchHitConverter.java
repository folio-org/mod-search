package org.folio.search.service.converter;

import static org.folio.search.utils.SearchUtils.PLAIN_MULTILANG_PREFIX;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ElasticsearchHitConverter {

  private final ObjectMapper objectMapper;

  /**
   * Converts elasticsearch document to the result class.
   *
   * <p>It removes all multi-language field data and leaves only src in the value,
   * removing sub-object and leaving one level only</p>
   *
   * @param <T> generic type for result class
   * @param elasticsearchHit elasticsearch hit as {@link Map} object
   * @param resultClass expected result class
   * @return constructed {@link T} object from incoming map.
   */
  public <T> T convert(Map<String, Object> elasticsearchHit, Class<T> resultClass) {
    if (MapUtils.isEmpty(elasticsearchHit)) {
      return objectMapper.convertValue(elasticsearchHit, resultClass);
    }
    return objectMapper.convertValue(processMap(elasticsearchHit), resultClass);
  }

  private static Map<String, Object> processMap(Map<String, Object> map) {
    var resultMap = new LinkedHashMap<String, Object>();
    for (var entry : map.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith(PLAIN_MULTILANG_PREFIX)) {
        resultMap.put(key.substring(PLAIN_MULTILANG_PREFIX.length()), processField(entry.getValue()));
        continue;
      }
      resultMap.putIfAbsent(key, processField(entry.getValue()));
    }
    return resultMap;
  }

  @SuppressWarnings("unchecked")
  private static Object processField(Object value) {
    if (value instanceof Map) {
      return processMap((Map<String, Object>) value);
    }
    if (value instanceof List) {
      List<Object> listValue = (List<Object>) value;
      return listValue.stream()
        .map(ElasticsearchHitConverter::processField)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    }
    return value;
  }

  private static String updateMultilangFieldName(String name) {
    return name.startsWith(PLAIN_MULTILANG_PREFIX) ? name.substring(PLAIN_MULTILANG_PREFIX.length()) : name;
  }
}
