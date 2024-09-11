package org.folio.search.service.converter.preprocessor;

import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.folio.search.utils.SearchConverterUtils.getResourceSource;
import static org.folio.search.utils.SearchConverterUtils.isUpdateEventForResourceSharing;
import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class InstanceEventPreProcessor implements EventPreProcessor {

  private final List<ChildResourceExtractor> resourceExtractors;

  @Override
  public List<ResourceEvent> preProcess(ResourceEvent event) {
    log.debug("preProcess::Starting instance event pre-processing");
    if (log.isDebugEnabled()) {
      log.debug("preProcess::Starting instance event pre-processing [{}]", event);
    }

    List<ResourceEvent> events = new ArrayList<>();
    events.add(event);

    if (isUpdateEventForResourceSharing(event)) {
      for (ChildResourceExtractor resourceExtractor : resourceExtractors) {
        events.addAll(resourceExtractor.prepareEventsOnSharing(event));
      }
    } else if (startsWith(getResourceSource(event), SOURCE_CONSORTIUM_PREFIX)) {
      log.debug(
        "preProcess::Finished instance event pre-processing. No additional events created for shadow instance.");
      return events;
    } else {
      for (ChildResourceExtractor resourceExtractor : resourceExtractors) {
        events.addAll(resourceExtractor.prepareEvents(event));
      }
    }

    if (log.isDebugEnabled()) {
      log.debug("preProcess::Finished instance event pre-processing. Events after: [{}], ", events);
    }
    return events;
  }

}
