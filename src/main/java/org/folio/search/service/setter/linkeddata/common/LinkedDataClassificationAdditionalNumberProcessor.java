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
import org.folio.search.domain.dto.LinkedDataClassification;
import org.folio.search.service.lccn.StringNormalizer;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataClassificationAdditionalNumberProcessor
  implements FieldProcessor<List<LinkedDataClassification>, Set<String>> {

  private final StringNormalizer stringNormalizer;

  @Override
  public Set<String> getFieldValue(List<LinkedDataClassification> linkedDataClassifications) {
    return ofNullable(linkedDataClassifications)
      .stream()
      .flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .map(LinkedDataClassification::getAdditionalNumber)
      .filter(Objects::nonNull)
      .map(stringNormalizer)
      .flatMap(Optional::stream)
      .collect(toCollection(LinkedHashSet::new));
  }
}
