package org.folio.search.service.setter.linkeddata.work;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.folio.search.domain.dto.LinkedDataIdentifier;
import org.folio.search.domain.dto.LinkedDataInstanceOnly;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.service.setter.instance.IsbnProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataIsbnProcessor;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class LinkedDataWorkIsbnProcessorTest {

  private final IsbnProcessor isbnProcessor = mock(IsbnProcessor.class);
  private final LinkedDataWorkIsbnProcessor processor =
    new LinkedDataWorkIsbnProcessor(new LinkedDataIsbnProcessor(isbnProcessor));

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
    when(isbnProcessor.normalizeIsbn("9781234567897")).thenReturn(List.of("9781234567897"));
    var instance = new LinkedDataInstanceOnly().identifiers(List.of(new LinkedDataIdentifier("9781234567897", "ISBN")));
    var work = new LinkedDataWork().instances(Arrays.asList(null, instance));

    var actual = processor.getFieldValue(work);

    assertThat(actual).isEqualTo(Set.of("9781234567897"));
  }
}
