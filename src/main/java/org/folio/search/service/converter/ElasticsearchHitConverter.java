package org.folio.search.service.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.utils.SearchUtils;
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
    var processedMap = new LinkedHashMap<String, Object>();
    elasticsearchHit.forEach((fieldName, fieldValue) -> processedMap.put(fieldName, processField(fieldValue)));
    return objectMapper.convertValue(processedMap, resultClass);
  }

  private static Map<String, Object> processMap(Map<String, Object> map) {
    var resultMap = new LinkedHashMap<String, Object>();
    map.forEach((fieldName, fieldValue) -> resultMap.put(fieldName, processField(fieldValue)));
    return resultMap;
  }

  @SuppressWarnings("unchecked")
  private static Object processField(Object value) {
    if (value instanceof Map && !isMultiLanguageField(value)) {
      return processMap((Map<String, Object>) value);
    }
    if (value instanceof List) {
      List<Object> listValue = (List<Object>) value;
      return listValue.stream()
        .map(ElasticsearchHitConverter::processField)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    }
    return isMultiLanguageField(value) ? getSourceMultilangValue(value) : value;
  }

  @SuppressWarnings("unchecked")
  private static boolean isMultiLanguageField(Object fieldValue) {
    return fieldValue instanceof Map && ((Map<String, Object>) fieldValue).containsKey(
      SearchUtils.MULTILANG_SOURCE_SUBFIELD);
  }

  @SuppressWarnings("unchecked")
  private static Object getSourceMultilangValue(Object fieldValue) {
    return ((Map<String, Object>) fieldValue).get(SearchUtils.MULTILANG_SOURCE_SUBFIELD);
  }
}
