package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Tags;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.JsonConverter;

@RequiredArgsConstructor
public abstract class AbstractTagsProcessor implements FieldProcessor<List<String>> {

  protected static final TypeReference<Tags> TAGS_TYPE_REFERENCE = new TypeReference<>() {};
  protected final JsonConverter jsonConverter;

  @Override
  public List<String> getFieldValue(Map<String, Object> eventBody) {
    return getTags(eventBody).stream()
      .filter(StringUtils::isNotBlank)
      .map(StringUtils::trim)
      .distinct()
      .collect(toList());
  }

  /**
   * Returns list of tags from event body.
   *
   * @param eventBody resource event body as {@link Map}
   * @return list with tag values
   */
  protected abstract List<String> getTags(Map<String, Object> eventBody);

  protected List<String> getTagsAsObjectFromValueByKey(Map<String, Object> eventBody, String key) {
    return getValueAsList(eventBody, key).stream()
      .map(this::getTagFromInnerResource)
      .flatMap(Collection::stream)
      .collect(toList());
  }

  @SuppressWarnings("unchecked")
  private static List<Object> getValueAsList(Map<String, Object> eventBody, String key) {
    if (eventBody == null) {
      return emptyList();
    }
    var value = eventBody.get(key);
    return (value instanceof List) ? (List<Object>) value : emptyList();
  }

  private List<String> getTagFromInnerResource(Object resourceValue) {
    if (!(resourceValue instanceof Map)) {
      return emptyList();
    }
    var tagsValue = ((Map<?, ?>) resourceValue).get("tags");
    return Optional.ofNullable(jsonConverter.convert(tagsValue, TAGS_TYPE_REFERENCE))
      .map(Tags::getTagList)
      .orElse(emptyList());
  }
}
