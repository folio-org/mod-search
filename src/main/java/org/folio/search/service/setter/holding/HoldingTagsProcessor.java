package org.folio.search.service.setter.holding;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

import java.util.stream.Stream;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Tags;
import org.folio.search.service.setter.instance.AbstractTagsProcessor;
import org.springframework.stereotype.Component;

@Component
public class HoldingTagsProcessor extends AbstractTagsProcessor {

  @Override
  protected Stream<Tags> getTags(Instance instance) {
    var holdings = instance.getHoldings();
    return isNotEmpty(holdings) ? holdings.stream().map(Holding::getTags) : Stream.empty();
  }
}
