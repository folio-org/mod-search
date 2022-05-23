package org.folio.search.integration;

import static java.util.Collections.emptyList;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
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
  private final JsonConverter jsonConverter;
  private final KafkaTemplate<String, ResourceEvent> kafkaTemplate;

  public void prepareAndSendContributorEvents(List<ResourceEvent> resourceEvents) {
    if (resourceEvents != null && !resourceEvents.isEmpty()) {
      resourceEvents.stream()
        .map(this::getContributorEvents)
        .flatMap(List::stream)
        .forEach(kafkaTemplate::send);
    }
  }

  private List<ProducerRecord<String, ResourceEvent>> getContributorEvents(ResourceEvent event) {
    var type = new TypeReference<List<Contributor>>() { };
    var oldContributors = getContributors(getOldAsMap(event), type);
    var newContributors = getContributors(getNewAsMap(event), type);

    return Stream.of(
        prepareContributorEvents(event, subtract(newContributors, oldContributors), CREATE),
        prepareContributorEvents(event, subtract(oldContributors, newContributors), DELETE))
      .flatMap(List::stream)
      .collect(Collectors.toList());
  }

  private List<Contributor> getContributors(Map<String, Object> objectMap, TypeReference<List<Contributor>> type) {
    return jsonConverter.convert(getObject(objectMap, INSTANCE_CONTRIBUTORS_FIELD_NAME, emptyList()), type);
  }

  private List<ProducerRecord<String, ResourceEvent>> prepareContributorEvents(ResourceEvent evt,
                                                                               Set<Contributor> contributors,
                                                                               ResourceEventType type) {
    var tenantId = evt.getTenant();
    var topicName = getTenantTopicName(INSTANCE_CONTRIBUTOR_TOPIC_NAME, tenantId);
    var instanceId = getResourceEventId(evt);
    return contributors.stream()
      .map(contributor -> prepareResourceEvent(contributor, instanceId, type, tenantId))
      .map(resourceEvent -> new ProducerRecord<>(topicName, resourceEvent.getId(), resourceEvent))
      .collect(Collectors.toList());
  }

  private ResourceEvent prepareResourceEvent(Contributor contributor, String instanceId, ResourceEventType type,
                                             String tenantId) {
    var id = getContributorId(tenantId, contributor);
    var contributorEvent = ContributorEvent.builder()
      .id(id)
      .instanceId(instanceId)
      .name(contributor.getName())
      .nameTypeId(contributor.getContributorNameTypeId())
      .typeId(contributor.getContributorTypeId())
      .build();
    var eventBody = new ResourceEvent().id(id).type(type).tenant(tenantId);
    return type == CREATE ? eventBody._new(contributorEvent) : eventBody.old(contributorEvent);
  }

  private String getContributorId(String tenantId, Contributor contributor) {
    return sha1Hex(tenantId + "|" + //NOSONAR
      contributor.getContributorNameTypeId() + "|" + toRootLowerCase(contributor.getName()));
  }
}
