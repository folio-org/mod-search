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
class IsbnProcessorTest {

  private static final String ISBN_TYPE = "8261054f-be78-422d-bd51-4ed9f33c3422";

  @InjectMocks private IsbnProcessor isbnProcessor;
  @Spy private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);

  @MethodSource("rawIsbnDataProvider")
  @DisplayName("should get field value")
  @ParameterizedTest(name = "[{index}] initial={0}, expected={1}")
  void getFieldValue_positive(List<Map<String, Object>> identifiers, List<String> expected) {
    var actual = isbnProcessor.getFieldValue(mapOf("identifiers", identifiers));
    assertThat(actual).isEqualTo(expected);
  }

  private static Stream<Arguments> rawIsbnDataProvider() {
    return Stream.of(
      arguments(emptyList(), emptyList()),
      arguments(null, emptyList()),
      arguments(List.of(isbnIdentifier("  ")), emptyList()),
      arguments(List.of(isbnIdentifier("047144250X")), List.of("047144250X", "9780471442509")),
      arguments(List.of(isbnIdentifier("978-0-471-44250-9")), List.of("9780471442509")),
      arguments(List.of(isbnIdentifier("9780471442509 (cloth : alk. paper)")),
        List.of("9780471442509 (cloth : alk. paper)")),
      arguments(List.of(isbnIdentifier("978-0-471-44250-9 (cloth : alk. paper)")),
        List.of("9780471442509 (cloth : alk. paper)")),
      arguments(List.of(identifier("issn", "0747-0088")), emptyList())
    );
  }

  private static Map<String, Object> identifier(String type, String value) {
    return mapOf("identifierTypeId", type, "value", value);
  }

  private static Map<String, Object> isbnIdentifier(String value) {
    return identifier(ISBN_TYPE, value);
  }
}
