package org.folio.search.service.setter.linkeddata.common;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;
import static org.folio.search.domain.dto.LinkedDataIdentifier.TypeEnum.ISBN;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataIdentifier;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.instance.IsbnProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataIsbnProcessor implements FieldProcessor<List<LinkedDataIdentifier>, Set<String>> {

  private final IsbnProcessor isbnProcessor;

  @Override
  public Set<String> getFieldValue(List<LinkedDataIdentifier> identifiers) {
    return ofNullable(identifiers)
      .stream()
      .flatMap(Collection::stream)
      .filter(i -> ISBN.equals(i.getType()))
      .map(LinkedDataIdentifier::getValue)
      .filter(Objects::nonNull)
      .map(isbnProcessor::normalizeIsbn)
      .flatMap(Collection::stream)
      .collect(toCollection(LinkedHashSet::new));
  }
}
