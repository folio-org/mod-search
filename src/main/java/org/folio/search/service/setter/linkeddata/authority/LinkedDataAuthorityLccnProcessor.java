package org.folio.search.service.setter.linkeddata.authority;

import static java.util.stream.Collectors.toCollection;
import static org.folio.search.domain.dto.LinkedDataAuthorityIdentifiersInner.TypeEnum.LCCN;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataAuthority;
import org.folio.search.domain.dto.LinkedDataAuthorityIdentifiersInner;
import org.folio.search.service.lccn.LccnNormalizer;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataAuthorityLccnProcessor implements FieldProcessor<LinkedDataAuthority, Set<String>> {

  private final LccnNormalizer lccnNormalizer;

  @Override
  public Set<String> getFieldValue(LinkedDataAuthority linkedDataAuthority) {
    return Optional.of(linkedDataAuthority)
      .map(LinkedDataAuthority::getIdentifiers)
      .orElseGet(Collections::emptyList)
      .stream()
      .filter(i -> LCCN.equals(i.getType()))
      .map(LinkedDataAuthorityIdentifiersInner::getValue)
      .filter(Objects::nonNull)
      .map(lccnNormalizer)
      .flatMap(Optional::stream)
      .collect(toCollection(LinkedHashSet::new));
  }
}
