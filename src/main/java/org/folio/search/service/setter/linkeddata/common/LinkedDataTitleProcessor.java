package org.folio.search.service.setter.linkeddata.common;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.LinkedDataTitle;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class LinkedDataTitleProcessor implements FieldProcessor<List<LinkedDataTitle>, Set<String>> {

  @Override
  public Set<String> getFieldValue(List<LinkedDataTitle> linkedDataTitles) {
    return ofNullable(linkedDataTitles)
      .stream()
      .flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .map(LinkedDataTitle::getValue)
      .filter(StringUtils::isNotBlank)
      .map(String::trim)
      .collect(toCollection(LinkedHashSet::new));
  }

}
