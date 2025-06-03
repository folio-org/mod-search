package org.folio.search.service.setter.linkeddata.common;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.LinkedDataContributor;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class LinkedDataContributorProcessor implements FieldProcessor<List<LinkedDataContributor>, Set<String>> {

  @Override
  public Set<String> getFieldValue(List<LinkedDataContributor> contributors) {
    return ofNullable(contributors)
      .stream()
      .flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .map(LinkedDataContributor::getName)
      .filter(StringUtils::isNotBlank)
      .map(String::trim)
      .collect(toCollection(LinkedHashSet::new));
  }
}
