package org.folio.search.service.setter.linkeddata.authority;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataAuthority;
import org.folio.search.service.lccn.StringNormalizer;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.instance.IsbnProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataAuthorityIdentifierProcessor implements FieldProcessor<LinkedDataAuthority, Set<String>> {
  private static final String ISBN = "ISBN";
  private final IsbnProcessor isbnProcessor;
  private final StringNormalizer stringNormalizer;

  @Override
  public Set<String> getFieldValue(LinkedDataAuthority linkedDataAuthority) {
    return ofNullable(linkedDataAuthority.getIdentifiers())
      .stream()
      .flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .filter(i -> nonNull(i.getValue()))
      .flatMap(i -> ISBN.equals(i.getType())
        ? isbnProcessor.normalizeIsbn(i.getValue()).stream()
        : stringNormalizer.apply(i.getValue()).stream()
      )
      .collect(toCollection(LinkedHashSet::new));
  }
}
