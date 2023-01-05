package org.folio.search.service.converter.preprocessor;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.apache.commons.collections4.MapUtils.getObject;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import one.util.streamex.StreamEx;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.event.SubjectResourceEvent;
import org.folio.search.utils.CollectionUtils;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InstanceEventPreProcessor implements EventPreProcessor {

  public static final String SUBJECTS_FIELD = "subjects";

  private static final TypeReference<List<SubjectResourceEvent>> TYPE_REFERENCE = new TypeReference<>() { };

  private final JsonConverter jsonConverter;

  @Override
  public List<ResourceEvent> process(ResourceEvent event) {
    var oldSubjects = extractSubjects(getOldAsMap(event));
    var newSubjects = extractSubjects(getNewAsMap(event));
    var tenantId = event.getTenant();
    var collect = StreamEx.of(event)
      .append(getSubjectsAsStreamSubtracting(newSubjects, oldSubjects, tenantId, CREATE))
      .append(getSubjectsAsStreamSubtracting(oldSubjects, newSubjects, tenantId, DELETE))
      .collect(toList());
    return collect;
  }

  private List<SubjectResourceEvent> extractSubjects(Map<String, Object> objectMap) {
    var contributorsObject = getObject(objectMap, SUBJECTS_FIELD, emptyList());
    var subjectResourceEvents = jsonConverter.convert(contributorsObject, TYPE_REFERENCE);
    subjectResourceEvents.forEach(
      subjectResourceEvent -> {
        subjectResourceEvent.setInstanceId(getResourceEventId(objectMap));
        subjectResourceEvent.setValue(StringUtils.trim(subjectResourceEvent.getValue()));
      });
    return subjectResourceEvents;
  }

  private Stream<ResourceEvent> getSubjectsAsStreamSubtracting(List<SubjectResourceEvent> subjects,
                                                               List<SubjectResourceEvent> subjectsToRemove,
                                                               String tenantId, ResourceEventType eventType) {
    return CollectionUtils.subtract(subjects, subjectsToRemove).stream()
      .filter(subject -> StringUtils.isNotBlank(subject.getValue()))
      .map(subject -> convertToSubjectEvent(subject, tenantId, eventType));
  }

  private ResourceEvent convertToSubjectEvent(SubjectResourceEvent subject, String tenantId, ResourceEventType type) {
    var id = sha256Hex(StringUtils.toRootLowerCase(subject.getValue() + subject.getAuthorityId()));
    subject.setId(id);
    var resourceEvent = new ResourceEvent().type(type).tenant(tenantId).id(id)
      .resourceName(INSTANCE_SUBJECT_RESOURCE);
    var body = jsonConverter.convert(subject, Map.class);
    return type == CREATE ? resourceEvent._new(body) : resourceEvent.old(body);
  }
}
