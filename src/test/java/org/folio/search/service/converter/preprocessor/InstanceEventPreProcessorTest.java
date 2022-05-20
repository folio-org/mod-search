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
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.resourceEvent;

import java.util.List;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class InstanceEventPreProcessorTest {

  @InjectMocks private InstanceEventPreProcessor preProcessor;

  @Test
  void process_positive() {
    var resourceEvent = resourceEvent(INSTANCE_RESOURCE, mapOf("id", RESOURCE_ID, "subjects", List.of("sub1", "sub2")));
    var actual = preProcessor.process(resourceEvent);
    assertThat(actual).isEqualTo(List.of(
      resourceEvent,
      resourceEvent(sha256Hex("sub1"), INSTANCE_SUBJECT_RESOURCE, mapOf("subject", "sub1")),
      resourceEvent(sha256Hex("sub2"), INSTANCE_SUBJECT_RESOURCE, mapOf("subject", "sub2"))));
  }

  @Test
  void process_positive_emptySubjects() {
    var resourceEvent = resourceEvent(INSTANCE_RESOURCE, mapOf("id", RESOURCE_ID, "subjects", emptyList()));
    var actual = preProcessor.process(resourceEvent);
    assertThat(actual).isEqualTo(List.of(resourceEvent));
  }

  @Test
  void process_positive_collectionWithEmptySubject() {
    var resourceEvent = resourceEvent(INSTANCE_RESOURCE, mapOf("id", RESOURCE_ID, "subjects", List.of("  ")));
    var actual = preProcessor.process(resourceEvent);
    assertThat(actual).isEqualTo(List.of(resourceEvent));
  }

  @Test
  void process_positive_deleteEvent() {
    var old = mapOf("id", TENANT_ID, "subjects", List.of("s1", "s2"));
    var resourceEvent = resourceEvent(RESOURCE_ID, INSTANCE_RESOURCE, DELETE, null, old);
    var actual = preProcessor.process(resourceEvent);
    assertThat(actual).isEqualTo(List.of(
      resourceEvent,
      resourceEvent(sha256Hex("s1"), INSTANCE_SUBJECT_RESOURCE, DELETE, null, mapOf("subject", "s1")),
      resourceEvent(sha256Hex("s2"), INSTANCE_SUBJECT_RESOURCE, DELETE, null, mapOf("subject", "s2"))
    ));
  }

  @Test
  void process_positive_updateEvent() {
    var oldData = mapOf("id", RESOURCE_ID, "subjects", List.of("s1", "s2"));
    var newData = mapOf("id", RESOURCE_ID, "subjects", List.of("s2", "s3"));
    var resourceEvent = resourceEvent(RESOURCE_ID, INSTANCE_RESOURCE, UPDATE, newData, oldData);
    var actual = preProcessor.process(resourceEvent);
    assertThat(actual).isEqualTo(List.of(
      resourceEvent,
      resourceEvent(sha256Hex("s3"), INSTANCE_SUBJECT_RESOURCE, CREATE, mapOf("subject", "s3"), null),
      resourceEvent(sha256Hex("s1"), INSTANCE_SUBJECT_RESOURCE, DELETE, null, mapOf("subject", "s1"))
    ));
  }
}
