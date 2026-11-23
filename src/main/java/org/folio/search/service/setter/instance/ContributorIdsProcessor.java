package org.folio.search.service.setter.instance;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.ContributorUtils;
import org.springframework.stereotype.Component;

@Component
public class ContributorIdsProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getContributors())
      .map(this::getContributorId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  private String getContributorId(Contributor contributor) {
    var name = ContributorUtils.prepareName(contributor.getName());
    if (isBlank(name)) {
      return null;
    }
    return ContributorUtils.getContributorId(name, contributor.getContributorNameTypeId(),
      contributor.getAuthorityId());
  }
}
