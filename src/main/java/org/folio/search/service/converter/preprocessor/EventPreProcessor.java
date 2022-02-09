package org.folio.search.service.converter.preprocessor;

import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.ResourceEvent;

public interface EventPreProcessor {

  /**
   * Processes given resource and provides {@link Map} with generated resource events.
   *
   * @param event - resource event to process as {@link ResourceEvent} object
   * @return map with resource events, where key is the resource name and value is the {@link List} with generated
   *   {@link ResourceEvent} objects
   */
  List<ResourceEvent> process(ResourceEvent event);
}
