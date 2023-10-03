package org.folio.search.integration;

import static java.util.Collections.singletonList;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.test.type.UnitTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaMessageProducerTest {

  @InjectMocks
  private KafkaMessageProducer producer;
  @Spy
  private JsonConverter jsonConverter = new JsonConverter(new ObjectMapper());
  @Mock
  private KafkaTemplate<String, ResourceEvent> kafkaTemplate;
  @Mock
  private ConsortiumTenantService tenantService;

  @Test
  void shouldSendTwoSubjectEvents_whenSubjectChanged() {
    var instanceId = randomId();
    var oldSubjectObject = subjectObject("Medicine");
    var newSubjectObject = subjectObject("Anthropology");
    var resourceEvent = resourceEvent(instanceId, INSTANCE_RESOURCE, UPDATE,
      instanceObjectWithSubjects(instanceId, newSubjectObject),
      instanceObjectWithSubjects(instanceId, oldSubjectObject)
    );
    producer.prepareAndSendSubjectEvents(singletonList(resourceEvent));

    verify(kafkaTemplate, times(2)).send(ArgumentMatchers.<ProducerRecord<String, ResourceEvent>>any());
  }

  @Test
  void shouldSendNoSubjectEvents_whenSubjectNotChanged() {
    var instanceId = randomId();
    var name = "Medicine";
    var oldContributorObject = subjectObject(name);
    var newContributorObject = subjectObject(name);
    var resourceEvent = resourceEvent(instanceId, INSTANCE_RESOURCE, UPDATE,
      instanceObjectWithSubjects(instanceId, newContributorObject),
      instanceObjectWithSubjects(instanceId, oldContributorObject)
    );
    producer.prepareAndSendSubjectEvents(singletonList(resourceEvent));

    verify(kafkaTemplate, never()).send(ArgumentMatchers.<ProducerRecord<String, ResourceEvent>>any());
  }

  @NullAndEmptySource
  @ParameterizedTest
  void shouldSendNoSubjectEvents_whenInstanceIdIsBlank(String instanceId) {
    var oldSubjectObject = subjectObject("Medicine");
    var newSubjectObject = subjectObject("Anthropology");
    var resourceEvent = resourceEvent(instanceId, INSTANCE_RESOURCE, UPDATE,
      instanceObjectWithSubjects(instanceId, newSubjectObject),
      instanceObjectWithSubjects(instanceId, oldSubjectObject)
    );
    producer.prepareAndSendSubjectEvents(singletonList(resourceEvent));

    verify(kafkaTemplate, never()).send(ArgumentMatchers.<ProducerRecord<String, ResourceEvent>>any());
  }

  @Test
  void shouldSendTwoContributorEvents_whenContributorChanged() {
    var instanceId = randomId();
    var typeId = randomId();
    var oldContributorObject = contributorObject(typeId, "Vader, Dart");
    var newContributorObject = contributorObject(typeId, "Skywalker, Luke");
    var resourceEvent = resourceEvent(instanceId, INSTANCE_RESOURCE, UPDATE,
      instanceObjectWithContributors(instanceId, newContributorObject),
      instanceObjectWithContributors(instanceId, oldContributorObject)
    );
    producer.prepareAndSendContributorEvents(singletonList(resourceEvent));

    verify(kafkaTemplate, times(2)).send(ArgumentMatchers.<ProducerRecord<String, ResourceEvent>>any());
  }

  @Test
  void shouldSendNoContributorEvents_whenContributorNotChanged() {
    var instanceId = randomId();
    var typeId = randomId();
    var name = "Vader, Dart";
    var oldContributorObject = contributorObject(typeId, name);
    var newContributorObject = contributorObject(typeId, name);
    var resourceEvent = resourceEvent(instanceId, INSTANCE_RESOURCE, UPDATE,
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
    var resourceEvent = resourceEvent(instanceId, INSTANCE_RESOURCE, UPDATE,
      instanceObjectWithContributors(instanceId, newContributorObject),
      instanceObjectWithContributors(instanceId, oldContributorObject)
    );
    producer.prepareAndSendContributorEvents(singletonList(resourceEvent));

    verify(kafkaTemplate, never()).send(ArgumentMatchers.<ProducerRecord<String, ResourceEvent>>any());
  }

  @Test
  void prepareAndSendContributorEvents_positive_instancesWithoutContributors() {
    var instanceId = randomId();
    var resourceEvent = resourceEvent(instanceId, INSTANCE_RESOURCE, CREATE, mapOf("id", instanceId), null);
    producer.prepareAndSendContributorEvents(singletonList(resourceEvent));

    verify(kafkaTemplate, never()).send(ArgumentMatchers.<ProducerRecord<String, ResourceEvent>>any());
  }

  @Test
  void prepareAndSendContributorEvents_positive_contributorWithoutTypeNameId() {
    var instanceId = randomId();
    var instanceObject = instanceObjectWithContributors(instanceId, mapOf("name", "John Smith"));
    var resourceEvent = resourceEvent(instanceId, INSTANCE_RESOURCE, CREATE, instanceObject, null);
    producer.prepareAndSendContributorEvents(singletonList(resourceEvent));

    verify(kafkaTemplate).send(ArgumentMatchers.<ProducerRecord<String, ResourceEvent>>any());
  }

  @NotNull
  private Map<String, String> instanceObjectWithContributors(String id, Map<String, String> contributorObject) {
    return mapOf("id", id, "contributors", List.of(contributorObject));
  }

  @NotNull
  private Map<String, String> instanceObjectWithSubjects(String id, Map<String, String> subjectObject) {
    return mapOf("id", id, "subjects", List.of(subjectObject), "source", "CONSORTIUM-FOLIO");
  }

  @NotNull
  private Map<String, String> contributorObject(String typeId, String name) {
    return mapOf("contributorNameTypeId", typeId, "name", name);
  }

  @NotNull
  private Map<String, String> subjectObject(String name) {
    return mapOf("value", name);
  }
}
