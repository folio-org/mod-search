package org.folio.search.service.setter.linkeddata.work;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.folio.search.domain.dto.LinkedDataInstanceOnly;
import org.folio.search.domain.dto.LinkedDataTitle;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.service.setter.linkeddata.common.LinkedDataTitleProcessor;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class LinkedDataWorkTitleProcessorTest {

  private final LinkedDataWorkTitleProcessor processor =
    new LinkedDataWorkTitleProcessor(new LinkedDataTitleProcessor());

  @Test
  void getFieldValue_positive_returnsEmptySet_whenWorkHasNoTitlesAndNoInstances() {
    var actual = processor.getFieldValue(new LinkedDataWork());

    assertThat(actual).isEmpty();
  }

  @Test
  void getFieldValue_positive_returnsWorkTitles_whenInstanceTitlesAreNull() {
    var work = new LinkedDataWork()
      .titles(List.of(new LinkedDataTitle().value("work title")))
      .instances(List.of(new LinkedDataInstanceOnly()));

    var actual = processor.getFieldValue(work);

    assertThat(actual).isEqualTo(Set.of("work title"));
  }

  @Test
  void getFieldValue_positive_combinesWorkAndInstanceTitles() {
    var work = new LinkedDataWork()
      .titles(List.of(new LinkedDataTitle().value("work title")))
      .instances(List.of(
        new LinkedDataInstanceOnly(),
        new LinkedDataInstanceOnly().titles(List.of(new LinkedDataTitle().value("instance title")))));

    var actual = processor.getFieldValue(work);

    assertThat(actual).containsExactlyInAnyOrder("work title", "instance title");
  }
}
