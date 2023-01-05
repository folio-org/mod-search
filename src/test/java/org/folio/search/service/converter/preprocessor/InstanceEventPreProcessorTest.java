package org.folio.search.service.converter.preprocessor;

import static java.util.Collections.emptyList;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.resourceEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.event.SubjectResourceEvent;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class InstanceEventPreProcessorTest {

  @Spy
  private JsonConverter jsonConverter = new JsonConverter(new ObjectMapper());
  @InjectMocks
  private InstanceEventPreProcessor preProcessor;

  @Test
  void process_positive() {
    var resourceEvent = resourceEvent(INSTANCE_RESOURCE,
      mapOf("id", RESOURCE_ID, "subjects", List.of(subject("sub1"), subject("sub2"))));
    var actual = preProcessor.process(resourceEvent);
    assertThat(actual).isEqualTo(List.of(
      resourceEvent,
      getSubjectCreateEvent("sub1", null),
      getSubjectCreateEvent("sub2", null)
    ));
  }

  @Test
  void process_positive_updateEvent() {
    var authorityId = UUID.randomUUID().toString();
    var oldData = mapOf("id", RESOURCE_ID, "subjects", List.of(subject("s1", authorityId), subject("s2")));
    var newData = mapOf("id", RESOURCE_ID, "subjects", List.of(subject("s2"), subject("s3", authorityId)));
    var resourceEvent = resourceEvent(RESOURCE_ID, INSTANCE_RESOURCE, UPDATE, newData, oldData);
    var actual = preProcessor.process(resourceEvent);
    assertThat(actual).isEqualTo(List.of(
      resourceEvent,
      getSubjectCreateEvent("s3", authorityId),
      getSubjectDeleteEvent("s1", authorityId)
    ));
  }

  @Test
  void process_positive_emptySubjects() {
    var resourceEvent = resourceEvent(INSTANCE_RESOURCE, mapOf("id", RESOURCE_ID, "subjects", emptyList()));
    var actual = preProcessor.process(resourceEvent);
    assertThat(actual).isEqualTo(List.of(resourceEvent));
  }

  @Test
  void process_positive_collectionWithEmptySubject() {
    var resourceEvent = resourceEvent(INSTANCE_RESOURCE, mapOf("id", RESOURCE_ID, "subjects", List.of(subject("  "))));
    var actual = preProcessor.process(resourceEvent);
    assertThat(actual).isEqualTo(List.of(resourceEvent));
  }

  @Test
  void process_positive_deleteEvent() {
    var old = mapOf("id", RESOURCE_ID, "subjects", List.of(subject("s1"), subject("s2")));
    var resourceEvent = resourceEvent(RESOURCE_ID, INSTANCE_RESOURCE, DELETE, null, old);
    var actual = preProcessor.process(resourceEvent);
    assertThat(actual).isEqualTo(List.of(
      resourceEvent,
      getSubjectDeleteEvent("s1", null),
      getSubjectDeleteEvent("s2", null)
    ));
  }

  private Map<String, String> subject(String value, String authorityId) {
    return mapOf("value", value, "authorityId", authorityId);
  }

  private Map<String, String> subject(String value) { return subject(value, null); }

  private ResourceEvent getSubjectCreateEvent(String value, String authId) {
    var id = sha256Hex(value + authId);
    return resourceEvent(id, INSTANCE_SUBJECT_RESOURCE, CREATE,
      jsonConverter.convert(new SubjectResourceEvent(id, value, authId, RESOURCE_ID), Map.class), null);
  }

  private ResourceEvent getSubjectDeleteEvent(String value, String authId) {
    var id = sha256Hex(value + authId);
    return resourceEvent(id, INSTANCE_SUBJECT_RESOURCE, DELETE, null,
      jsonConverter.convert(new SubjectResourceEvent(id, value, authId, RESOURCE_ID), Map.class));
  }
}
