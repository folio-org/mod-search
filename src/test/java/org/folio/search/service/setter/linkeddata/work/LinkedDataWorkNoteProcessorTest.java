package org.folio.search.service.setter.linkeddata.work;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.folio.search.domain.dto.LinkedDataInstanceOnly;
import org.folio.search.domain.dto.LinkedDataNote;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.service.setter.linkeddata.common.LinkedDataNoteProcessor;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class LinkedDataWorkNoteProcessorTest {

  private final LinkedDataWorkNoteProcessor processor =
    new LinkedDataWorkNoteProcessor(new LinkedDataNoteProcessor());

  @Test
  void getFieldValue_positive_returnsEmptySet_whenWorkHasNoNotesAndNoInstances() {
    var actual = processor.getFieldValue(new LinkedDataWork());

    assertThat(actual).isEmpty();
  }

  @Test
  void getFieldValue_positive_returnsWorkNotes_whenInstanceNotesAreNull() {
    var work = new LinkedDataWork()
      .notes(List.of(new LinkedDataNote().value("work note")))
      .instances(List.of(new LinkedDataInstanceOnly()));

    var actual = processor.getFieldValue(work);

    assertThat(actual).isEqualTo(Set.of("work note"));
  }

  @Test
  void getFieldValue_positive_returnsNotesOfInstancesThatHaveThem_whenOtherInstanceNotesAreNull() {
    var work = new LinkedDataWork()
      .instances(List.of(
        new LinkedDataInstanceOnly(),
        new LinkedDataInstanceOnly().notes(List.of(new LinkedDataNote().value("instance note")))));

    var actual = processor.getFieldValue(work);

    assertThat(actual).isEqualTo(Set.of("instance note"));
  }

  @Test
  void getFieldValue_positive_combinesWorkAndInstanceNotes() {
    var work = new LinkedDataWork()
      .notes(List.of(new LinkedDataNote().value("work note")))
      .instances(List.of(new LinkedDataInstanceOnly().notes(List.of(new LinkedDataNote().value("instance note")))));

    var actual = processor.getFieldValue(work);

    assertThat(actual).containsExactlyInAnyOrder("work note", "instance note");
  }
}
