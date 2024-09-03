package org.folio.search.integration;

import static java.util.Collections.singletonList;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.apache.logging.log4j.util.Strings.toRootLowerCase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;
import static org.folio.search.utils.SearchUtils.SOURCE_FIELD;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.event.ContributorResourceEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.testing.type.UnitTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaMessageProducerTest {

  private static final ArgumentCaptor<ProducerRecord> CAPTOR = ArgumentCaptor.forClass(ProducerRecord.class);

  @InjectMocks
  private KafkaMessageProducer producer;
  @Spy
  private JsonConverter jsonConverter = new JsonConverter(new ObjectMapper());
  @Mock
  private KafkaTemplate<String, ResourceEvent> kafkaTemplate;
  @Mock
  private ConsortiumTenantService consortiumTenantService;


  @Test
  void shouldSendTwoContributorEvents_whenContributorChanged() {
    var instanceId = randomId();
    var typeId = randomId();
    var oldContributorObject = contributorObject(typeId, "Vader, Dart");
    var newContributorObject = contributorObject(typeId, "Skywalker, Luke");
    var resourceEvent = resourceEvent(instanceId, ResourceType.INSTANCE, UPDATE,
      instanceObjectWithContributors(instanceId, newContributorObject),
      instanceObjectWithContributors(instanceId, oldContributorObject)
    );

    producer.prepareAndSendContributorEvents(singletonList(resourceEvent));

    verify(kafkaTemplate, times(2)).send(ArgumentMatchers.<ProducerRecord<String, ResourceEvent>>any());
  }

  @Test
  void shouldSendDeleteOldContributorEventOfMemberTenant_whenInstanceUpdatedWithSharing() {
    var instanceId = randomId();
    var typeId = randomId();
    var oldContributorObject = contributorObject(typeId, "Skywalker, Luke");
    var newContributorObject = contributorObject(typeId, "Skywalker, Luke");
    var resourceEvent = resourceEvent(instanceId, ResourceType.INSTANCE, UPDATE,
      instanceObjectWithContributors(instanceId, newContributorObject, SOURCE_CONSORTIUM_PREFIX + "FOLIO"),
      instanceObjectWithContributors(instanceId, oldContributorObject, "FOLIO")
    );
    final var expectedOld = ContributorResourceEvent.builder()
      .id(sha1Hex(typeId + "|" + toRootLowerCase(oldContributorObject.get("name") + "|null")))
      .typeId(typeId)
      .nameTypeId(typeId)
      .shared(false)
      .name(oldContributorObject.get("name"))
      .instanceId(instanceId)
      .build();

    producer.prepareAndSendContributorEvents(singletonList(resourceEvent));

    verify(kafkaTemplate).send(CAPTOR.capture());
    var record = (ResourceEvent) CAPTOR.getValue().value();
    assertThat(List.of(record))
      .extracting(ResourceEvent::getType, ResourceEvent::getNew)
      .containsExactlyInAnyOrder(tuple(DELETE, null));
    assertThat((ContributorResourceEvent) record.getOld()).isEqualTo(expectedOld);
  }

  @Test
  void shouldSendNoContributorEvents_whenInstanceWithConsortiumSourceCreated() {
    var instanceId = randomId();
    var typeId = randomId();
    var newContributorObject = contributorObject(typeId, "Skywalker, Luke");
    var resourceEvent = resourceEvent(instanceId, ResourceType.INSTANCE,
      instanceObjectWithContributors(instanceId, newContributorObject, SOURCE_CONSORTIUM_PREFIX + "FOLIO")
    );

    producer.prepareAndSendContributorEvents(singletonList(resourceEvent));

    verifyNoInteractions(kafkaTemplate);
  }

  @Test
  void shouldSendNoContributorEvents_whenContributorNotChanged() {
    var instanceId = randomId();
    var typeId = randomId();
    var name = "Vader, Dart";
    var oldContributorObject = contributorObject(typeId, name);
    var newContributorObject = contributorObject(typeId, name);
    var resourceEvent = resourceEvent(instanceId, ResourceType.INSTANCE, UPDATE,
      instanceObjectWithContributors(instanceId, newContributorObject),
      instanceObjectWithContributors(instanceId, oldContributorObject)
    );
    producer.prepareAndSendContributorEvents(singletonList(resourceEvent));

    verify(kafkaTemplate, never()).send(ArgumentMatchers.<ProducerRecord<String, ResourceEvent>>any());
  }

  @Test
  void shouldSendNoContributorEvents_whenChangedOnlyContributorTypeText() {
    var instanceId = randomId();
    var typeId = randomId();
    var name = "Vader, Dart";
    var oldContributorObject = contributorObject(typeId, name);
    var newContributorObject = contributorObject(typeId, name);
    newContributorObject.put("contributorTypeText", "text");
    var resourceEvent = resourceEvent(instanceId, ResourceType.INSTANCE, UPDATE,
      instanceObjectWithContributors(instanceId, newContributorObject),
      instanceObjectWithContributors(instanceId, oldContributorObject)
    );
    producer.prepareAndSendContributorEvents(singletonList(resourceEvent));

    verify(kafkaTemplate, never()).send(ArgumentMatchers.<ProducerRecord<String, ResourceEvent>>any());
  }

  @Test
  void prepareAndSendContributorEvents_positive_instancesWithoutContributors() {
    var instanceId = randomId();
    var resourceEvent = resourceEvent(instanceId, ResourceType.INSTANCE, CREATE, mapOf("id", instanceId), null);
    producer.prepareAndSendContributorEvents(singletonList(resourceEvent));

    verify(kafkaTemplate, never()).send(ArgumentMatchers.<ProducerRecord<String, ResourceEvent>>any());
  }

  @Test
  void prepareAndSendContributorEvents_positive_contributorWithoutTypeNameId() {
    var instanceId = randomId();
    var instanceObject = instanceObjectWithContributors(instanceId, mapOf("name", "John Smith"));
    var resourceEvent = resourceEvent(instanceId, ResourceType.INSTANCE, CREATE, instanceObject, null);
    producer.prepareAndSendContributorEvents(singletonList(resourceEvent));

    verify(kafkaTemplate).send(ArgumentMatchers.<ProducerRecord<String, ResourceEvent>>any());
  }

  @NotNull
  private Map<String, String> instanceObjectWithContributors(String id, Map<String, String> contributorObject) {
    return mapOf(ID_FIELD, id, "contributors", List.of(contributorObject));
  }

  @NotNull
  private Map<String, String> instanceObjectWithContributors(String id,
                                                             Map<String, String> contributorObject,
                                                             String source) {
    return mapOf(ID_FIELD, id, "contributors", List.of(contributorObject), SOURCE_FIELD, source);
  }

  @NotNull
  private Map<String, String> instanceObjectWithSubjects(String id, Map<String, String> subjectObject) {
    return mapOf(ID_FIELD, id, "subjects", List.of(subjectObject), SOURCE_FIELD, "FOLIO");
  }

  @NotNull
  private Map<String, String> instanceObjectWithSubjects(String id, Map<String, String> subjectObject, String source) {
    return mapOf(ID_FIELD, id, "subjects", List.of(subjectObject), SOURCE_FIELD, source);
  }

  @NotNull
  private Map<String, String> contributorObject(String typeId, String name) {
    return mapOf("contributorNameTypeId", typeId, "name", name, "contributorTypeId", typeId);
  }

  @NotNull
  private Map<String, String> subjectObject(String name) {
    return mapOf("value", name);
  }
}
