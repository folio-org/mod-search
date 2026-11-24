package org.folio.search.service.setter.instance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Subject;
import org.folio.search.model.index.SubjectKey;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class SubjectIdsProcessorTest {

  private final SubjectIdsProcessor processor = new SubjectIdsProcessor();

  @Test
  void getFieldValue_positive_sameValueDifferentSourceAndType() {
    var instance = new Instance().subjects(List.of(
      new Subject().value("Music").sourceId("s1").typeId("t1"),
      new Subject().value("Music").sourceId("s2").typeId("t1"),
      new Subject().value("Music").sourceId("s1").typeId("t2")));

    assertThat(processor.getFieldValue(instance)).hasSize(3);
  }

  @Test
  void getFieldValue_positive_idsMatchSubjectKey() {
    var instance = new Instance().subjects(List.of(
      new Subject().value("Music").authorityId("a").sourceId("s").typeId("t"),
      new Subject().value("Art")));

    assertThat(processor.getFieldValue(instance)).containsExactlyInAnyOrder(
      SubjectKey.of("Music", "a", "s", "t").orElseThrow().id(),
      SubjectKey.of("Art", null, null, null).orElseThrow().id());
  }

  @Test
  void getFieldValue_positive_blankValueSkipped() {
    var instance = new Instance().subjects(List.of(new Subject().value("  "), new Subject()));

    assertThat(processor.getFieldValue(instance)).isEmpty();
  }

  @Test
  void getFieldValue_positive_nullAndEmptySubjects() {
    assertThat(processor.getFieldValue(new Instance())).isEmpty();
    assertThat(processor.getFieldValue(new Instance().subjects(List.of()))).isEmpty();
  }

  @Test
  void getFieldValue_positive_duplicatesCollapse() {
    var instance = new Instance().subjects(List.of(new Subject().value("Music"), new Subject().value("Music")));

    assertThat(processor.getFieldValue(instance)).hasSize(1);
  }
}
