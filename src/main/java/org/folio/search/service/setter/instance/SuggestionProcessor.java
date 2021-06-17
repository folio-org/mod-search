package org.folio.search.service.setter.instance;

import static java.util.stream.Collectors.toList;
import static org.folio.search.utils.SearchUtils.toSafeStream;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceAlternativeTitles;
import org.folio.search.domain.dto.InstanceContributors;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class SuggestionProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return getSuggestTerms(instance);
  }

  protected static Set<String> getSuggestTerms(Instance instance) {
    var terms = new LinkedHashSet<String>();

    addIfNotNull(terms, instance.getTitle());
    addIfNotNull(terms, instance.getIndexTitle());
    addIfNotEmpty(terms, instance.getSubjects());
    addIfNotEmpty(terms, instance.getSeries());
    addIfNotEmpty(terms, getContributorNames(instance));
    addIfNotEmpty(terms, getAlternativeTitles(instance));

    return terms;
  }

  private static List<String> getContributorNames(Instance instance) {
    return toSafeStream(instance.getContributors()).map(InstanceContributors::getName).collect(toList());
  }

  private static List<String> getAlternativeTitles(Instance instance) {
    return toSafeStream(instance.getAlternativeTitles())
      .map(InstanceAlternativeTitles::getAlternativeTitle)
      .collect(toList());
  }

  private static <T> void addIfNotEmpty(Collection<T> list, Collection<T> coll) {
    if (CollectionUtils.isNotEmpty(coll)) {
      list.addAll(coll);
    }
  }

  private static <T> void addIfNotNull(Collection<T> list, T value) {
    if (value != null) {
      list.add(value);
    }
  }
}
