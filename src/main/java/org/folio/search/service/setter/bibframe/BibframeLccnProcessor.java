package org.folio.search.service.setter.bibframe;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;
import static org.folio.search.domain.dto.BibframeInstancesInnerIdentifiersInner.TypeEnum.LCCN;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.Bibframe;
import org.folio.search.domain.dto.BibframeInstancesInnerIdentifiersInner;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BibframeLccnProcessor implements FieldProcessor<Bibframe, Set<String>> {

  private final LccnNormalizer lccnNormalizer;

  @Override
  public Set<String> getFieldValue(Bibframe bibframe) {
    return ofNullable(bibframe.getInstances()).stream()
      .flatMap(Collection::stream)
      .filter(i -> nonNull(i.getIdentifiers()))
      .flatMap(i -> i.getIdentifiers().stream())
      .filter(i -> LCCN.equals(i.getType()))
      .map(BibframeInstancesInnerIdentifiersInner::getValue)
      .filter(Objects::nonNull)
      .map(lccnNormalizer::normalizeLccn)
      .flatMap(Optional::stream)
      .collect(toCollection(LinkedHashSet::new));
  }

}
