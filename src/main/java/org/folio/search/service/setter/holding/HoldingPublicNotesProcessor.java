package org.folio.search.service.setter.holding;

import static org.folio.search.utils.CollectionUtils.toSafeStream;

import java.util.Collection;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Note;
import org.folio.search.service.setter.instance.AbstractPublicNotesProcessor;
import org.springframework.stereotype.Component;

@Component
public class HoldingPublicNotesProcessor extends AbstractPublicNotesProcessor {

  @Override
  protected Stream<Note> getNotes(Instance instance) {
    return toSafeStream(instance.getHoldings())
      .map(Holding::getNotes)
      .filter(CollectionUtils::isNotEmpty)
      .flatMap(Collection::stream);
  }
}
