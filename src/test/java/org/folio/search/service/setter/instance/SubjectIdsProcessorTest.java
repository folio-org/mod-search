package org.folio.search.service.setter.instance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Subject;
import org.folio.search.utils.ShaUtils;
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
  void getFieldValue_positive_formulaMatchesSha() {
    var instance = new Instance().subjects(List.of(
      new Subject().value("Music").authorityId("a").sourceId("s").typeId("t"),
      new Subject().value("Art")));

    assertThat(processor.getFieldValue(instance)).containsExactlyInAnyOrder(
      ShaUtils.sha("Music", "a", "s", "t"), ShaUtils.sha("Art", "", "", ""));
  }

  @Test
  void getFieldValue_positive_valueIsTrimmed() {
    var instance = new Instance().subjects(List.of(new Subject().value("  Music ")));

    assertThat(processor.getFieldValue(instance)).containsExactly(ShaUtils.sha("Music", "", "", ""));
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
