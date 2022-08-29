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

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.event.ContributorEvent;
import org.folio.search.utils.JsonConverter;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaMessageProducer {

  private static final String INSTANCE_CONTRIBUTOR_TOPIC_NAME = "search.instance-contributor";
  private static final TypeReference<List<Contributor>> TYPE_REFERENCE = new TypeReference<>() { };
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

  private List<ProducerRecord<String, ResourceEvent>> getContributorEvents(ResourceEvent event) {
    var tenantId = event.getTenant();
    var instanceId = getResourceEventId(event);
    var oldContributors = getContributorEvents(getOldAsMap(event), instanceId, tenantId);
    var newContributors = getContributorEvents(getNewAsMap(event), instanceId, tenantId);

    return Stream.of(
        prepareContributorEvents(subtract(newContributors, oldContributors), CREATE, tenantId),
        prepareContributorEvents(subtract(oldContributors, newContributors), DELETE, tenantId))
      .flatMap(List::stream)
      .collect(Collectors.toList());
  }

  private List<ContributorEvent> getContributorEvents(Map<String, Object> objectMap, String instanceId,
                                                      String tenantId) {
    return extractContributors(objectMap).stream()
      .map(contributor -> toContributorEvent(contributor, instanceId, tenantId))
      .collect(Collectors.toList());
  }

  private ContributorEvent toContributorEvent(Contributor contributor, String instanceId, String tenantId) {
    var id = getContributorId(tenantId, contributor);
    return ContributorEvent.builder()
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

  private List<ProducerRecord<String, ResourceEvent>> prepareContributorEvents(Set<ContributorEvent> contributors,
                                                                               ResourceEventType type,
                                                                               String tenantId) {
    var topicName = getTenantTopicName(INSTANCE_CONTRIBUTOR_TOPIC_NAME, tenantId);
    return contributors.stream()
      .map(contributor -> prepareResourceEvent(contributor, type, tenantId))
      .map(resourceEvent -> new ProducerRecord<>(topicName, resourceEvent.getId(), resourceEvent))
      .collect(Collectors.toList());
  }

  private ResourceEvent prepareResourceEvent(ContributorEvent contributorEvent, ResourceEventType type,
                                             String tenantId) {
    var eventBody = new ResourceEvent().id(contributorEvent.getId()).type(type).tenant(tenantId);
    return type == CREATE ? eventBody._new(contributorEvent) : eventBody.old(contributorEvent);
  }

  private String getContributorId(String tenantId, Contributor contributor) {
    return sha1Hex(tenantId
      + "|" + contributor.getContributorNameTypeId()
      + "|" + toRootLowerCase(contributor.getName())
      + "|" + contributor.getAuthorityId());
  }
}
