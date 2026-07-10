package org.folio.search.service.setter.linkeddata.common;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataIdentifier;
import org.folio.search.service.lccn.StringNormalizer;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataLccnProcessor implements FieldProcessor<List<LinkedDataIdentifier>, Set<String>> {
  private static final String LCCN = "LCCN";
  private final StringNormalizer stringNormalizer;

  @Override
  public Set<String> getFieldValue(List<LinkedDataIdentifier> linkedDataIdentifiers) {
    return ofNullable(linkedDataIdentifiers)
      .stream()
      .flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .filter(i -> LCCN.equals(i.getType()))
      .map(LinkedDataIdentifier::getValue)
      .filter(Objects::nonNull)
      .map(stringNormalizer)
      .flatMap(Optional::stream)
      .collect(toCollection(LinkedHashSet::new));
  }
}
