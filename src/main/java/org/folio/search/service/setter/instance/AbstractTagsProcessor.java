package org.folio.search.service.setter.instance;

import static java.util.stream.Collectors.toCollection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Tags;
import org.folio.search.service.setter.FieldProcessor;

@RequiredArgsConstructor
public abstract class AbstractTagsProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return getTagsValuesAsStream(instance)
      .filter(StringUtils::isNotBlank)
      .map(String::trim)
      .collect(toCollection(LinkedHashSet::new));
  }

  /**
   * Returns list of tags from event body.
   *
   * @param instance resource event body as {@link Map}
   * @return list with tag values
   */
  protected abstract Stream<Tags> getTags(Instance instance);

  private Stream<String> getTagsValuesAsStream(Instance instance) {
    return getTags(instance)
      .filter(Objects::nonNull)
      .map(Tags::getTagList)
      .filter(CollectionUtils::isNotEmpty)
      .flatMap(Collection::stream);
  }
}
