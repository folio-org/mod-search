package org.folio.search.service.setter.holding;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Holding;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HoldingsCallNumberComponentsProcessor implements FieldProcessor<Set<String>> {
  private final JsonConverter jsonConverter;

  @Override
  public Set<String> getFieldValue(Map<String, Object> eventBody) {
    var holdings = jsonConverter.convert(eventBody.get("holdings"), new TypeReference<List<Holding>>() {});

    if (holdings == null) {
      return emptySet();
    }

    return holdings.stream()
      .map(hr -> Stream.of(hr.getCallNumberPrefix(), hr.getCallNumber(), hr.getCallNumberSuffix())
        .filter(StringUtils::isNotBlank)
        .map(String::trim)
        .collect(joining(" ")))
      .filter(StringUtils::isNotBlank)
      .collect(toSet());
  }
}
