package org.folio.search.service.setter.item;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Item;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EffectiveCallNumberComponentsProcessor implements FieldProcessor<Set<String>> {
  private final JsonConverter jsonConverter;

  @Override
  public Set<String> getFieldValue(Map<String, Object> eventBody) {
    var items = jsonConverter.convert(eventBody.get("items"), new TypeReference<List<Item>>() {});

    if (items == null) {
      return emptySet();
    }

    return items.stream()
      .map(Item::getEffectiveCallNumberComponents)
      .filter(Objects::nonNull)
      .map(cn -> Stream.of(cn.getPrefix(), cn.getCallNumber(), cn.getSuffix())
        .filter(StringUtils::isNotBlank)
        .map(String::trim)
        .collect(joining(" ")))
      .filter(StringUtils::isNotBlank)
      .collect(toSet());
  }
}
