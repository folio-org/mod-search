package org.folio.search.service.setter.linkeddata.common;

import static java.util.Optional.ofNullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.LinkedDataTitle;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class LinkedDataSortTitleProcessor implements FieldProcessor<List<LinkedDataTitle>, String> {

  @Override
  public String getFieldValue(List<LinkedDataTitle> linkedDataTitles) {
    return ofNullable(linkedDataTitles)
      .stream()
      .flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .map(LinkedDataTitle::getValue)
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.joining());
  }

}
