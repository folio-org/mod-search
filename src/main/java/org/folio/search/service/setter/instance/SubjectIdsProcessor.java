package org.folio.search.service.setter.instance;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;
import static org.folio.search.utils.SearchUtils.prepareForExpectedFormat;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Subject;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.ShaUtils;
import org.springframework.stereotype.Component;

@Component
public class SubjectIdsProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getSubjects())
      .map(this::getSubjectId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  private String getSubjectId(Subject subject) {
    var value = prepareForExpectedFormat(subject.getValue(), 255);
    if (isBlank(value)) {
      return null;
    }

    return ShaUtils.sha(value, Objects.toString(subject.getAuthorityId(), EMPTY),
      Objects.toString(subject.getSourceId(), EMPTY), Objects.toString(subject.getTypeId(), EMPTY));
  }
}
