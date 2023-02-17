package org.folio.search.integration;

import static java.util.Collections.emptyList;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.collections4.MapUtils.getObject;
import static org.apache.commons.lang3.StringUtils.toRootLowerCase;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.KafkaUtils.getTenantTopicName;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;
import static org.folio.search.utils.SearchUtils.INSTANCE_CONTRIBUTORS_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import one.util.streamex.StreamEx;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.event.ContributorResourceEvent;
import org.folio.search.model.event.SubjectResourceEvent;
import org.folio.search.utils.CollectionUtils;
import org.folio.search.utils.JsonConverter;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaMessageProducer {

  private static final String SUBJECTS_FIELD = "subjects";
  private static final String INSTANCE_CONTRIBUTOR_TOPIC_NAME = "search.instance-contributor";
  private static final String INSTANCE_SUBJECTS_TOPIC_NAME = "search.instance-subjects";
  private static final TypeReference<List<Contributor>> TYPE_REFERENCE = new TypeReference<>() { };
  private static final TypeReference<List<SubjectResourceEvent>> TYPE_REFERENCE_SUBJECT = new TypeReference<>() { };
  private final JsonConverter jsonConverter;
  private final KafkaTemplate<String, ResourceEvent> kafkaTemplate;

  public void prepareAndSendContributorEvents(List<ResourceEvent> resourceEvents) {
    if (isNotEmpty(resourceEvents)) {
      resourceEvents.stream()
        .filter(Objects::nonNull)
        .map(this::getContributorEvents)
        .flatMap(List::stream)
        .forEach(kafkaTemplate::send);
    }
  }

  public void prepareAndSendSubjectEvents(List<ResourceEvent> resourceEvents) {
    if (isNotEmpty(resourceEvents)) {
      resourceEvents.stream()
        .filter(Objects::nonNull)
        .map(this::getSubjectsEvents)
        .flatMap(List::stream)
        .forEach(kafkaTemplate::send);
    }
  }

  private List<ProducerRecord<String, ResourceEvent>> getSubjectsEvents(ResourceEvent event) {
    var oldSubjects = extractSubjects(getOldAsMap(event));
    var newSubjects = extractSubjects(getNewAsMap(event));
    var tenantId = event.getTenant();
    var subjectsCreate = getSubjectsAsStreamSubtracting(newSubjects, oldSubjects, tenantId, CREATE);
    var subjectsDelete = getSubjectsAsStreamSubtracting(oldSubjects, newSubjects, tenantId, DELETE);
    return StreamEx.of(subjectsCreate)
      .append(subjectsDelete)
      .map(resourceEvent -> new ProducerRecord<>(INSTANCE_SUBJECTS_TOPIC_NAME, resourceEvent.getId(), resourceEvent))
      .toList();
  }

  private List<SubjectResourceEvent> extractSubjects(Map<String, Object> objectMap) {
    var contributorsObject = getObject(objectMap, SUBJECTS_FIELD, emptyList());
    var subjectResourceEvents = jsonConverter.convert(contributorsObject, TYPE_REFERENCE_SUBJECT);
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
    var id = sha1Hex(StringUtils.toRootLowerCase(subject.getValue() + subject.getAuthorityId()));
    subject.setId(id);
    var resourceEvent = new ResourceEvent().type(type).tenant(tenantId).id(id)
      .resourceName(INSTANCE_SUBJECT_RESOURCE);
    var body = jsonConverter.convert(subject, Map.class);
    return type == CREATE ? resourceEvent._new(body) : resourceEvent.old(body);
  }

  private List<ProducerRecord<String, ResourceEvent>> getContributorEvents(ResourceEvent event) {
    var tenantId = event.getTenant();
    var instanceId = getResourceEventId(event);
    var oldContributors = getContributorEvents(getOldAsMap(event), instanceId, tenantId);
    var newContributors = getContributorEvents(getNewAsMap(event), instanceId, tenantId);

    return Stream.of(
        prepareContributorEvents(subtract(newContributors, oldContributors), CREATE, tenantId),
        prepareContributorEvents(subtract(oldContributors, newContributors), DELETE, tenantId))
      .flatMap(List::stream)
      .toList();
  }

  private List<ContributorResourceEvent> getContributorEvents(Map<String, Object> objectMap, String instanceId,
                                                              String tenantId) {
    return extractContributors(objectMap).stream()
      .map(contributor -> toContributorEvent(contributor, instanceId, tenantId))
      .toList();
  }

  private ContributorResourceEvent toContributorEvent(Contributor contributor, String instanceId, String tenantId) {
    var id = getContributorId(tenantId, contributor);
    return ContributorResourceEvent.builder()
      .id(id)
      .instanceId(instanceId)
      .name(contributor.getName())
      .nameTypeId(contributor.getContributorNameTypeId())
      .typeId(contributor.getContributorTypeId())
      .authorityId(contributor.getAuthorityId())
      .build();
  }

  private List<Contributor> extractContributors(Map<String, Object> objectMap) {
    var contributorsObject = getObject(objectMap, INSTANCE_CONTRIBUTORS_FIELD_NAME, emptyList());
    return jsonConverter.convert(contributorsObject, TYPE_REFERENCE);
  }

  private List<ProducerRecord<String, ResourceEvent>> prepareContributorEvents(Set<ContributorResourceEvent> events,
                                                                               ResourceEventType type,
                                                                               String tenantId) {
    var topicName = getTenantTopicName(INSTANCE_CONTRIBUTOR_TOPIC_NAME, tenantId);
    return events.stream()
      .map(contributor -> prepareResourceEvent(contributor, type, tenantId))
      .map(resourceEvent -> new ProducerRecord<>(topicName, resourceEvent.getId(), resourceEvent))
      .toList();
  }

  private ResourceEvent prepareResourceEvent(ContributorResourceEvent contributorEvent, ResourceEventType type,
                                             String tenantId) {
    var eventBody = new ResourceEvent().id(contributorEvent.getId()).type(type).tenant(tenantId);
    return type == CREATE ? eventBody._new(contributorEvent) : eventBody.old(contributorEvent);
  }

  private String getContributorId(String tenantId, Contributor contributor) {
    return sha1Hex(tenantId //NOSONAR
      + "|" + contributor.getContributorNameTypeId()
      + "|" + toRootLowerCase(contributor.getName())
      + "|" + contributor.getAuthorityId());
  }
}
