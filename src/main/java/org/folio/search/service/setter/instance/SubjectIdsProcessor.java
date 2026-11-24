package org.folio.search.service.setter.instance;

import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.Instance;
import org.folio.search.model.index.SubjectKey;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class SubjectIdsProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getSubjects())
      .map(subject -> SubjectKey.of(subject.getValue(), subject.getAuthorityId(), subject.getSourceId(),
        subject.getTypeId()))
      .flatMap(Optional::stream)
      .map(SubjectKey::id)
      .collect(Collectors.toSet());
  }
}
