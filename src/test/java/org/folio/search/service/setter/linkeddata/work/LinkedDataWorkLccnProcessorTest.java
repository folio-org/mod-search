package org.folio.search.service.setter.linkeddata.work;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.folio.search.domain.dto.LinkedDataIdentifier;
import org.folio.search.domain.dto.LinkedDataInstanceOnly;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.service.setter.linkeddata.common.LinkedDataLccnProcessor;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class LinkedDataWorkLccnProcessorTest {

  private final LinkedDataWorkLccnProcessor processor =
    new LinkedDataWorkLccnProcessor(new LinkedDataLccnProcessor(Optional::of));

  @Test
  void getFieldValue_positive_returnsEmptySet_whenWorkHasNoInstances() {
    var actual = processor.getFieldValue(new LinkedDataWork());

    assertThat(actual).isEmpty();
  }

  @Test
  void getFieldValue_positive_skipsInstancesWithNullIdentifiers() {
    var work = new LinkedDataWork().instances(List.of(new LinkedDataInstanceOnly()));

    var actual = processor.getFieldValue(work);

    assertThat(actual).isEmpty();
  }

  @Test
  void getFieldValue_positive_skipsNullInstances() {

    var instance = new LinkedDataInstanceOnly().identifiers(List.of(new LinkedDataIdentifier("n79021425", "LCCN")));
    var work = new LinkedDataWork().instances(Arrays.asList(null, instance));

    var actual = processor.getFieldValue(work);

    assertThat(actual).isEqualTo(Set.of("n79021425"));
  }
}
