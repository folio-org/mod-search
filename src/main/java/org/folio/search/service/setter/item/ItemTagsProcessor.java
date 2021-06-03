package org.folio.search.service.setter.item;

import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.Tags;
import org.folio.search.service.setter.instance.AbstractTagsProcessor;
import org.springframework.stereotype.Component;

@Component
public class ItemTagsProcessor extends AbstractTagsProcessor {

  @Override
  protected Stream<Tags> getTags(Instance instance) {
    var items = instance.getItems();
    return CollectionUtils.isNotEmpty(items) ? items.stream().map(Item::getTags) : Stream.empty();
  }
}
