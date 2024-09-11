package org.folio.search.service.converter.preprocessor.extractor;

import java.util.List;
import org.folio.search.domain.dto.ResourceEvent;

public interface ChildResourceExtractor {

  List<ResourceEvent> prepareEvents(ResourceEvent resource);

  List<ResourceEvent> prepareEventsOnSharing(ResourceEvent resource);
}
