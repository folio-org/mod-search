package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.InstanceContributors;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class SortContributorsSetterTest {
  private static final String CONTRIBUTORS = "contributors";

  private final JsonConverter converter = new JsonConverter(OBJECT_MAPPER);
  private final SortContributorsProcessor contributorsSetter =
    new SortContributorsProcessor(converter);

  @Test
  void shouldReturnFirstContributorIfNoPrimary() {
    var map = contributorsToMap(new InstanceContributors().name("first"),
      new InstanceContributors().name("second"));

    assertThat(contributorsSetter.getFieldValue(map)).isEqualTo("first");
  }

  @Test
  void shouldReturnPrimaryContributorRegardlessPosition() {
    var map = contributorsToMap(new InstanceContributors().name("first"),
      new InstanceContributors().name("second").primary(true));

    assertThat(contributorsSetter.getFieldValue(map)).isEqualTo("second");
  }

  @Test
  void shouldReturnNullIfEmptyMap() {
    assertNull(contributorsSetter.getFieldValue(null));
  }

  @Test
  void shouldReturnNullIfNoContributors() {
    assertNull(contributorsSetter.getFieldValue(Map.of("title", "title")));
  }

  @Test
  void shouldReturnNullIfContributorsIsEmpty() {
    assertNull(contributorsSetter.getFieldValue(Map.of(CONTRIBUTORS, emptyList())));
  }

  private Map<String, Object> contributorsToMap(InstanceContributors... contributors) {
    List<Object> contributorsArray = new ArrayList<>();

    for (InstanceContributors contributor : contributors) {
      contributorsArray.add(converter.convert(contributor, new TypeReference<Map<String, Object>>() {}));
    }

    return Map.of(CONTRIBUTORS, contributorsArray);
  }
}
