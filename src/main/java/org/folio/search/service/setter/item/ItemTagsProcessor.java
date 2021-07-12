package org.folio.search.service.setter.item;

import static org.folio.search.utils.CollectionUtils.toSafeStream;

import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.Tags;
import org.folio.search.service.setter.instance.AbstractTagsProcessor;
import org.springframework.stereotype.Component;

@Component
public class ItemTagsProcessor extends AbstractTagsProcessor {

  @Override
  protected Stream<Tags> getTags(Instance instance) {
    return toSafeStream(instance.getItems()).map(Item::getTags);
  }
}
