package org.folio.search.service.converter;

import static java.util.Collections.singletonMap;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.toList;
import static org.folio.search.utils.CollectionUtils.getValuesByPath;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InstanceSubjectPreProcessor {

  public List<ResourceEvent> process(ResourceEvent event) {
    var subjects = getValuesByPath(getNewAsMap(event), "subjects");
    return subjects.stream()
      .filter(StringUtils::isNotBlank)
      .map(subject -> convertToBrowseEvent(event.getTenant(), StringUtils.trim(subject)))
      .collect(toList());
  }

  private static ResourceEvent convertToBrowseEvent(String tenantId, String subject) {
    return new ResourceEvent().id(DigestUtils.sha256Hex(subject.toLowerCase(ROOT)))
      .type(ResourceEventType.CREATE).tenant(tenantId)
      .resourceName(INSTANCE_SUBJECT_RESOURCE)
      ._new(singletonMap("subject", subject));
  }
}
