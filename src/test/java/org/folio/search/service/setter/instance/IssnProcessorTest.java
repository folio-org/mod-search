package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IssnProcessorTest {

  private static final String ISSN_TYPE = "913300b2-03ed-469a-8179-c1092c991227";

  @InjectMocks private IssnProcessor issnProcessor;
  @Spy private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);

  @MethodSource("rawIssnDataProvider")
  @DisplayName("should get field value")
  @ParameterizedTest(name = "[{index}] initial={0}, expected={1}")
  void getFieldValue_positive(List<Map<String, Object>> identifiers, List<String> expected) {
    var actual = issnProcessor.getFieldValue(mapOf("identifiers", identifiers));
    assertThat(actual).isEqualTo(expected);
  }

  private static Stream<Arguments> rawIssnDataProvider() {
    return Stream.of(
      arguments(emptyList(), emptyList()),
      arguments(List.of(issnIdentifier("0317-8471")), List.of("0317-8471")),
      arguments(List.of(issnIdentifier(" 0317-8471 ")), List.of("0317-8471")),
      arguments(List.of(mapOf("identifierTypeId", "isbn", "value", "1234")), emptyList())
    );
  }

  private static Map<String, Object> issnIdentifier(String value) {
    return mapOf("identifierTypeId", ISSN_TYPE, "value", value);
  }
}
