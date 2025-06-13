package org.folio.search.service.setter.instance;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;
import static org.folio.search.utils.SearchUtils.prepareForExpectedFormat;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.Classification;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.ShaUtils;
import org.springframework.stereotype.Component;

@Component
public class ClassificationIdsProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getClassifications())
      .map(this::getClassificationId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  private String getClassificationId(Classification classification) {
    var classificationNumber = prepareForExpectedFormat(classification.getClassificationNumber(), 50);
    if (isBlank(classificationNumber)) {
      return null;
    }

    return ShaUtils.sha(classificationNumber, Objects.toString(classification.getClassificationTypeId(), EMPTY));
  }
}
