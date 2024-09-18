package org.folio.search.service.setter.linkeddata.common;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;
import static org.folio.search.domain.dto.LinkedDataIdentifier.TypeEnum.LCCN;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataIdentifier;
import org.folio.search.service.lccn.LccnNormalizer;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataLccnProcessor implements FieldProcessor<List<LinkedDataIdentifier>, Set<String>> {

  @Qualifier("lccnNormalizerStructureB")
  private final LccnNormalizer lccnNormalizer;

  @Override
  public Set<String> getFieldValue(List<LinkedDataIdentifier> linkedDataIdentifiers) {
    return ofNullable(linkedDataIdentifiers)
      .stream()
      .flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .filter(i -> LCCN.equals(i.getType()))
      .map(LinkedDataIdentifier::getValue)
      .filter(Objects::nonNull)
      .map(lccnNormalizer)
      .flatMap(Optional::stream)
      .collect(toCollection(LinkedHashSet::new));
  }
}
