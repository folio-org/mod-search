package org.folio.search.service.setter.instance;

import static org.folio.search.utils.SearchUtils.toSafeStream;

import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Note;
import org.springframework.stereotype.Component;

@Component
public class PublicNotesProcessor extends AbstractPublicNotesProcessor {

  @Override
  protected Stream<Note> getNotes(Instance instance) {
    return toSafeStream(instance.getNotes());
  }
}
