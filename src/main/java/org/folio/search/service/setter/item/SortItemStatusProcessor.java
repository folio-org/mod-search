package org.folio.search.service.setter.item;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
// Should be removed with renaming items -> item
public final class SortItemStatusProcessor implements FieldProcessor<Instance, List<String>> {

  @Override
  public List<String> getFieldValue(Instance instance) {
    var items = instance.getItems();

    //Secondary sort by title
    if (CollectionUtils.isEmpty(items)) {
      var indexTitle = instance.getIndexTitle();
      var result = isNotBlank(indexTitle) ? indexTitle : defaultIfBlank(instance.getTitle(), null);
      return List.of(result);
    }

    return items.stream()
      .map(item -> item.getStatus().getName())
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.toList());
  }
}
