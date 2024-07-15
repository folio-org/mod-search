package org.folio.search.service.setter.linkeddata.work;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;
import static org.folio.search.domain.dto.LinkedDataWorkInstancesInnerIdentifiersInner.TypeEnum.ISBN;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.domain.dto.LinkedDataWorkInstancesInnerIdentifiersInner;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.instance.IsbnProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataWorkIsbnProcessor implements FieldProcessor<LinkedDataWork, Set<String>> {

  private final IsbnProcessor isbnProcessor;

  @Override
  public Set<String> getFieldValue(LinkedDataWork linkedDataWork) {
    return ofNullable(linkedDataWork.getInstances()).stream()
      .flatMap(Collection::stream)
      .filter(i -> nonNull(i.getIdentifiers()))
      .flatMap(i -> i.getIdentifiers().stream())
      .filter(i -> ISBN.equals(i.getType()))
      .map(LinkedDataWorkInstancesInnerIdentifiersInner::getValue)
      .filter(Objects::nonNull)
      .map(isbnProcessor::normalizeIsbn)
      .flatMap(Collection::stream)
      .collect(toCollection(LinkedHashSet::new));
  }
}
