package org.folio.search.service.setter.instance;

import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Identifiers;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceAlternativeTitlesInner;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class KeywordValuesProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    var strings = new ArrayList<String>();
    strings.add(instance.getTitle());
    strings.add(instance.getIndexTitle());

    toStreamSafe(instance.getIdentifiers())
      .map(Identifiers::getValue)
      .forEach(strings::add);

    toStreamSafe(instance.getContributors())
      .map(Contributor::getName)
      .forEach(strings::add);

    toStreamSafe(instance.getAlternativeTitles())
      .map(InstanceAlternativeTitlesInner::getAlternativeTitle)
      .forEach(strings::add);

    strings.addAll(instance.getSeries());

    return strings.stream().filter(Objects::nonNull).collect(Collectors.toSet());
  }
}
