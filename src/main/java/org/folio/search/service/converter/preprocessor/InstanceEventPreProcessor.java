package org.folio.search.service.converter.preprocessor;

import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.utils.CollectionUtils.getValuesByPath;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import one.util.streamex.StreamEx;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InstanceEventPreProcessor implements EventPreProcessor {

  public static final String SUBJECT_FIELD = "subjects";
  public static final String INSTANCE_SUBJECT_BROWSE_FIELD = "subject";

  @Override
  public List<ResourceEvent> process(ResourceEvent event) {
    var oldSubjects = getValuesByPath(getOldAsMap(event), SUBJECT_FIELD);
    var newSubjects = getValuesByPath(getNewAsMap(event), SUBJECT_FIELD);
    var tenantId = event.getTenant();
    return StreamEx.of(event)
      .append(getSubjectsAsStreamSubtracting(newSubjects, oldSubjects, tenantId, CREATE))
      .append(getSubjectsAsStreamSubtracting(oldSubjects, newSubjects, tenantId, DELETE))
      .collect(toList());
  }

  private static Stream<ResourceEvent> getSubjectsAsStreamSubtracting(
    List<String> subjects, List<String> subjectsToRemove, String tenantId, ResourceEventType eventType) {
    var result = new LinkedHashSet<>(subjects);
    subjectsToRemove.forEach(result::remove);
    return result.stream()
      .filter(StringUtils::isNotBlank)
      .map(StringUtils::trim)
      .map(subject -> convertToBrowseEvent(subject, tenantId, eventType));
  }

  private static ResourceEvent convertToBrowseEvent(String subject, String tenantId, ResourceEventType type) {
    var resourceEvent = new ResourceEvent().type(type).tenant(tenantId)
      .id(sha256Hex(StringUtils.toRootLowerCase(subject)))
      .resourceName(INSTANCE_SUBJECT_RESOURCE);
    var body = singletonMap(INSTANCE_SUBJECT_BROWSE_FIELD, subject);
    return type == CREATE ? resourceEvent._new(body) : resourceEvent.old(body);
  }
}
