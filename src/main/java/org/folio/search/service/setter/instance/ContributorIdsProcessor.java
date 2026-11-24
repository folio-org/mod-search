package org.folio.search.service.setter.instance;

import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.Instance;
import org.folio.search.model.index.ContributorKey;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class ContributorIdsProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getContributors())
      .map(contributor -> ContributorKey.of(contributor.getName(), contributor.getContributorNameTypeId(),
        contributor.getAuthorityId()))
      .flatMap(Optional::stream)
      .map(ContributorKey::id)
      .collect(Collectors.toSet());
  }
}
