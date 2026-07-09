package org.folio.search.service.setter.linkeddata.authority;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.service.lccn.StringNormalizer;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataAuthorityTypeProcessor implements FieldProcessor<List<String>, Set<String>> {
  private final StringNormalizer stringNormalizer;

  @Override
  public Set<String> getFieldValue(List<String> types) {
    return ofNullable(types)
      .stream()
      .flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .flatMap(i -> stringNormalizer.apply(i).stream())
      .collect(toCollection(LinkedHashSet::new));
  }
}
