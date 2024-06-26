package org.folio.search.service.setter.bibframe.authority;

import static java.util.stream.Collectors.toCollection;
import static org.folio.search.domain.dto.BibframeAuthorityIdentifiersInner.TypeEnum.LCCN;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.BibframeAuthority;
import org.folio.search.domain.dto.BibframeAuthorityIdentifiersInner;
import org.folio.search.service.lccn.LccnNormalizer;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BibframeAuthorityLccnProcessor implements FieldProcessor<BibframeAuthority, Set<String>> {

  private final LccnNormalizer lccnNormalizer;

  @Override
  public Set<String> getFieldValue(BibframeAuthority bibframe) {
    return Optional.of(bibframe)
      .map(BibframeAuthority::getIdentifiers)
      .orElseGet(Collections::emptyList)
      .stream()
      .filter(i -> LCCN.equals(i.getType()))
      .map(BibframeAuthorityIdentifiersInner::getValue)
      .filter(Objects::nonNull)
      .map(lccnNormalizer)
      .flatMap(Optional::stream)
      .collect(toCollection(LinkedHashSet::new));
  }
}
