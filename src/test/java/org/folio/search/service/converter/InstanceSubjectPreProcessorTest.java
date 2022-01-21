package org.folio.search.service.converter;

import static java.util.Collections.emptyList;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;

import java.util.List;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class InstanceSubjectPreProcessorTest {

  private final InstanceSubjectPreProcessor preProcessor = new InstanceSubjectPreProcessor();

  @Test
  void process_positive() {
    var resourceEvent = resourceEvent(INSTANCE_RESOURCE, mapOf("id", randomId(), "subjects", List.of("sub1", "sub2")));
    var actual = preProcessor.process(resourceEvent);
    assertThat(actual).isEqualTo(List.of(
      resourceEvent(sha256Hex("sub1"), INSTANCE_SUBJECT_RESOURCE, mapOf("subject", "sub1")),
      resourceEvent(sha256Hex("sub2"), INSTANCE_SUBJECT_RESOURCE, mapOf("subject", "sub2"))));
  }

  @Test
  void process_positive_emptySubjects() {
    var resourceEvent = resourceEvent(INSTANCE_RESOURCE, mapOf("id", randomId(), "subjects", emptyList()));
    var actual = preProcessor.process(resourceEvent);
    assertThat(actual).isEmpty();
  }

  @Test
  void process_positive_collectionWithEmptySubject() {
    var resourceEvent = resourceEvent(INSTANCE_RESOURCE, mapOf("id", randomId(), "subjects", List.of("  ")));
    var actual = preProcessor.process(resourceEvent);
    assertThat(actual).isEmpty();
  }
}
