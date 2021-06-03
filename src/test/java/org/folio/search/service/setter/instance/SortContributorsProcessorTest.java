package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceContributors;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class SortContributorsProcessorTest {
  private final SortContributorsProcessor processor = new SortContributorsProcessor();

  @Test
  void shouldReturnFirstContributorIfNoPrimary() {
    var map = new Instance()
      .addContributorsItem(new InstanceContributors().name("first"))
      .addContributorsItem(new InstanceContributors().name("second"));

    assertThat(processor.getFieldValue(map)).isEqualTo("first");
  }

  @Test
  void shouldReturnPrimaryContributorRegardlessPosition() {
    var map = new Instance()
      .addContributorsItem(new InstanceContributors().name("first"))
      .addContributorsItem(new InstanceContributors().name("second").primary(true));

    assertThat(processor.getFieldValue(map)).isEqualTo("second");
  }

  @Test
  void shouldReturnNullIfEmptyMap() {
    assertNull(processor.getFieldValue(new Instance()));
  }

  @Test
  void shouldReturnNullIfNoContributors() {
    assertNull(processor.getFieldValue(new Instance().title("title")));
  }

  @Test
  void shouldReturnNullIfContributorsIsEmpty() {
    assertNull(processor.getFieldValue(new Instance().contributors(emptyList())));
  }
}
