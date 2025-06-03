package org.folio.search.service.setter.instance;

import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.Classification;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.SearchUtils;
import org.springframework.stereotype.Component;

@Component
public class ClassificationNumberProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    List<Classification> classifications = instance.getClassifications();
    return toStreamSafe(classifications)
      .map(Classification::getClassificationNumber)
      .map(SearchUtils::normalizeToAlphaNumeric)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }
}
