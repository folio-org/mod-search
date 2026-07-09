package org.folio.search.service.setter.linkeddata.authority;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataIdentifier;
import org.folio.search.service.lccn.StringNormalizer;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.instance.IsbnProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataAuthorityIdentifierProcessor implements FieldProcessor<List<LinkedDataIdentifier>, Set<String>> {
  private static final String ISBN = "ISBN";
  private final IsbnProcessor isbnProcessor;
  private final StringNormalizer stringNormalizer;

  @Override
  public Set<String> getFieldValue(List<LinkedDataIdentifier> linkedDataIdentifiers) {
    return ofNullable(linkedDataIdentifiers)
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
