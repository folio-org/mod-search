package org.folio.search.integration;

import static java.util.Collections.singletonList;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.utils.JsonConverter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class KafkaMessageProducerTest {

  @InjectMocks
  private KafkaMessageProducer producer;
  @Spy
  private JsonConverter jsonConverter = new JsonConverter(new ObjectMapper());
  @Mock
  private KafkaTemplate<String, ResourceEvent> kafkaTemplate;

  @Test
  void shouldSendTwoContributorEvents_whenContributorChanged() {
    var instanceId = randomId();
    var typeId = randomId();
    var oldContributorObject = contributorObject(typeId, "Vader, Dart");
    var newContributorObject = contributorObject(typeId, "Skywalker, Luke");
    var resourceEvent = resourceEvent(instanceId, INSTANCE_RESOURCE, UPDATE,
      instanceObject(instanceId, newContributorObject),
      instanceObject(instanceId, oldContributorObject)
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
      instanceObject(instanceId, newContributorObject),
      instanceObject(instanceId, oldContributorObject)
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
      instanceObject(instanceId, newContributorObject),
      instanceObject(instanceId, oldContributorObject)
    );
    producer.prepareAndSendContributorEvents(singletonList(resourceEvent));

    verify(kafkaTemplate, never()).send(ArgumentMatchers.<ProducerRecord<String, ResourceEvent>>any());
  }

  @NotNull
  private Map<String, String> instanceObject(String id, Map<String, String> contributorObject) {
    return mapOf("id", id, "contributors", List.of(contributorObject));
  }

  @NotNull
  private Map<String, String> contributorObject(String typeId, String name) {
    return mapOf("contributorNameTypeId", typeId, "name", name);
  }
}
