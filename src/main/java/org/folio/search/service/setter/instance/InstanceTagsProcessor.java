package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.InstanceTags;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InstanceTagsProcessor implements FieldProcessor<List<String>> {

  private final JsonConverter jsonConverter;

  @Override
  public List<String> getFieldValue(Map<String, Object> eventBody) {
    return Optional.ofNullable(MapUtils.getObject(eventBody, "tags"))
      .map(tags -> jsonConverter.convert(tags, new TypeReference<InstanceTags>() {}))
      .map(InstanceTags::getTagList)
      .map(this::getInstanceTags)
      .orElse(emptyList());
  }

  private List<String> getInstanceTags(List<String> tagList) {
    return tagList.stream()
      .filter(StringUtils::isNotBlank)
      .map(StringUtils::trim)
      .collect(toList());
  }
}
