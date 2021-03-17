package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.service.setter.instance.IsbnProcessor.ISBN_IDENTIFIER_NAMES;
import static org.folio.search.utils.TestConstants.INVALID_ISBN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestConstants.ISBN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.folio.search.repository.cache.InstanceIdentifierTypeCache;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IsbnProcessorTest {

  @InjectMocks private IsbnProcessor isbnProcessor;
  @Mock private InstanceIdentifierTypeCache cache;
  @Spy private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);

  @MethodSource("rawIsbnDataProvider")
  @DisplayName("should get field value")
  @ParameterizedTest(name = "[{index}] initial={0}, expected={1}")
  void getFieldValue_positive(List<Map<String, Object>> identifiers, List<String> expected) {
    when(cache.fetchIdentifierIds(ISBN_IDENTIFIER_NAMES))
      .thenReturn(Set.of(ISBN_IDENTIFIER_TYPE_ID, INVALID_ISBN_IDENTIFIER_TYPE_ID));

    var actual = isbnProcessor.getFieldValue(mapOf("identifiers", identifiers));
    assertThat(actual).isEqualTo(expected);
  }

  @MethodSource("emptyDataProvider")
  @DisplayName("should get field value when identifiers array is empty")
  @ParameterizedTest(name = "[{index}] initial={0}, expected={1}")
  void getFieldValue_emptyArray(List<Map<String, Object>> identifiers, List<String> expected) {
    var actual = isbnProcessor.getFieldValue(mapOf("identifiers", identifiers));
    assertThat(actual).isEqualTo(expected);
  }

  private static Stream<Arguments> emptyDataProvider() {
    return Stream.of(
        arguments(emptyList(), emptyList()),
        arguments(null, emptyList()));
  }

  private static Stream<Arguments> rawIsbnDataProvider() {
    return Stream.of(
      arguments(List.of(isbnIdentifier("  ")), emptyList()),
      arguments(List.of(isbnIdentifier("047144250X")), List.of("047144250X", "9780471442509")),
      arguments(List.of(invalidIsbnIdentifier("1 86197 271-7")), List.of("1861972717", "9781861972712")),
      arguments(List.of(invalidIsbnIdentifier("1 86197 2717")), List.of("1861972717")),
      arguments(List.of(invalidIsbnIdentifier("047144250X")), List.of("047144250X", "9780471442509")),
      arguments(List.of(isbnIdentifier("1 86197 271-7")), List.of("1861972717", "9781861972712")),
      arguments(List.of(isbnIdentifier("1-86-197 271-7")), List.of("1861972717")),
      arguments(List.of(isbnIdentifier("1-86-1*")), List.of("1861*")),
      arguments(List.of(isbnIdentifier("*86-1*")), List.of("*861*")),
      arguments(List.of(isbnIdentifier("  1-86-197 271-7  ")), List.of("1861972717")),
      arguments(List.of(isbnIdentifier("978-0-471-44250-9")), List.of("9780471442509")),
      arguments(List.of(isbnIdentifier("978 0 471 44250 9")), List.of("9780471442509")),
      arguments(List.of(isbnIdentifier("978  0  471  44250  9")), List.of("9780471442509")),
      arguments(List.of(isbnIdentifier("9780471442509 (alk. paper)")), List.of("9780471442509", "(alk. paper)")),
      arguments(List.of(isbnIdentifier("89780471442509 (alk. paper)")), List.of("89780471442509 (alk. paper)")),
      arguments(List.of(isbnIdentifier("978-0-471-44250-9 (alk. paper)")), List.of("9780471442509", "(alk. paper)")),
      arguments(List.of(isbnIdentifier("978 0 471 44250 9 (alk. paper)")), List.of("9780471442509", "(alk. paper)")),
      arguments(List.of(isbnIdentifier("978-0 4712 442509 (alk. paper)")), List.of("97804712442509 (alk. paper)")),
      arguments(List.of(isbnIdentifier("047144250X (paper)")), List.of("047144250X", "9780471442509", "(paper)")),
      arguments(List.of(isbnIdentifier("1 86197 271-7 (paper)")), List.of("1861972717", "9781861972712", "(paper)")),
      arguments(List.of(isbnIdentifier("1 86197 2717 (paper)")), List.of("1861972717 (paper)")),
      arguments(List.of(isbnIdentifier("1-86-197 2717 (paper)")), List.of("1861972717 (paper)")),
      arguments(List.of(identifier("issn", "0747-0088")), emptyList())
    );
  }

  private static Map<String, Object> identifier(String value, String type) {
    return mapOf("identifierTypeId", type, "value", value);
  }

  private static Map<String, Object> invalidIsbnIdentifier(String value) {
    return identifier(value, INVALID_ISBN_IDENTIFIER_TYPE_ID);
  }

  private static Map<String, Object> isbnIdentifier(String value) {
    return identifier(value, ISBN_IDENTIFIER_TYPE_ID);
  }
}
