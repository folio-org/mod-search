package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.IDENTIFIER_TYPES;
import static org.folio.search.utils.TestConstants.INVALID_ISBN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestConstants.ISBN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestUtils.identifier;
import static org.folio.search.utils.TestUtils.instanceWithIdentifiers;
import static org.folio.search.utils.TestUtils.setOf;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.domain.dto.Identifiers;
import org.folio.search.domain.dto.Instance;
import org.folio.search.integration.ReferenceDataService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IsbnProcessorTest {

  @InjectMocks
  private IsbnProcessor isbnProcessor;
  @Mock
  private ReferenceDataService referenceDataService;

  @MethodSource("isbnDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance instance, List<String> expected) {
    if (CollectionUtils.isNotEmpty(instance.getIdentifiers())) {
      var identifiers = setOf(ISBN_IDENTIFIER_TYPE_ID, INVALID_ISBN_IDENTIFIER_TYPE_ID);
      mockFetchReferenceData(identifiers);
    }

    var actual = isbnProcessor.getFieldValue(instance);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  @Test
  void getFieldValue_negative_failedToLoadReferenceData() {
    mockFetchReferenceData(emptySet());
    var actual = isbnProcessor.getFieldValue(instanceWithIdentifiers(isbn("123456")));
    assertThat(actual).isEmpty();
  }

  private static Stream<Arguments> isbnDataProvider() {
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("empty isbn identifier", instanceWithIdentifiers(isbn("")), emptyList()),
      arguments("blank isbn identifier", instanceWithIdentifiers(isbn("  ")), emptyList()),
      arguments("issn identifier='0747-0088'", instanceWithIdentifiers(identifier("issn", "0747-0088")), emptyList()),
      arguments("isbn identifier=null", instanceWithIdentifiers(isbn(null)), emptyList()),
      arguments("isbn identifier='047144250X'",
        instanceWithIdentifiers(isbn("047144250X")), List.of("047144250X", "9780471442509")),
      arguments("invalid isbn identifier='1 86197 271-7'",
        instanceWithIdentifiers(invalidIsbn("1 86197 271-7")), List.of("1861972717", "9781861972712")),
      arguments("invalid isbn identifier='1 86197 2717'",
        instanceWithIdentifiers(invalidIsbn("1 86197 2717")), List.of("1861972717")),
      arguments("invalid isbn identifier='047144250X'",
        instanceWithIdentifiers(invalidIsbn("047144250X")), List.of("047144250X", "9780471442509")),
      arguments("isbn identifier='1 86197 271-7'",
        instanceWithIdentifiers(isbn("1 86197 271-7")), List.of("1861972717", "9781861972712")),
      arguments("isbn identifier='1-86-197 271-7'",
        instanceWithIdentifiers(isbn("1-86-197 271-7")), List.of("1861972717")),
      arguments("isbn identifier='1-86-1*'",
        instanceWithIdentifiers(isbn("1-86-1*")), List.of("1861*")),
      arguments("isbn identifier='*86-1*'",
        instanceWithIdentifiers(isbn("*86-1*")), List.of("*861*")),
      arguments("isbn identifier='  1-86-197 271-7  '",
        instanceWithIdentifiers(isbn("  1-86-197 271-7  ")), List.of("1861972717")),
      arguments("isbn identifier='978-0-471-44250-9'",
        instanceWithIdentifiers(isbn("978-0-471-44250-9")), List.of("9780471442509")),
      arguments("isbn identifier='978 0 471 44250 9'",
        instanceWithIdentifiers(isbn("978 0 471 44250 9")), List.of("9780471442509")),
      arguments("isbn identifier='978  0  471  44250  9'",
        instanceWithIdentifiers(isbn("978  0  471  44250  9")), List.of("9780471442509")),
      arguments("isbn identifier=9780471442509 (alk. paper)'",
        instanceWithIdentifiers(isbn("9780471442509 (alk. paper)")), List.of("9780471442509", "(alk. paper)")),
      arguments("isbn identifier='89780471442509 (alk. paper)'",
        instanceWithIdentifiers(isbn("89780471442509 (alk. paper)")), List.of("89780471442509 (alk. paper)")),
      arguments("isbn identifier='978-0-471-44250-9 (alk. paper)'",
        instanceWithIdentifiers(isbn("978-0-471-44250-9 (alk. paper)")), List.of("9780471442509", "(alk. paper)")),
      arguments("isbn identifier='978 0 471 44250 9 (alk. paper)'",
        instanceWithIdentifiers(isbn("978 0 471 44250 9 (alk. paper)")), List.of("9780471442509", "(alk. paper)")),
      arguments("isbn identifier='978-0 4712 442509 (alk. paper)'",
        instanceWithIdentifiers(isbn("978-0 4712 442509 (alk. paper)")), List.of("97804712442509 (alk. paper)")),
      arguments("isbn identifier='047144250X (paper)'",
        instanceWithIdentifiers(isbn("047144250X (paper)")), List.of("047144250X", "9780471442509", "(paper)")),
      arguments("isbn identifier='1 86197 271-7 (paper)'",
        instanceWithIdentifiers(isbn("1 86197 271-7 (paper)")), List.of("1861972717", "9781861972712", "(paper)")),
      arguments("isbn identifier='1 86197 2717 (paper)'",
        instanceWithIdentifiers(isbn("1 86197 2717 (paper)")), List.of("1861972717 (paper)")),
      arguments("isbn identifier='1-86-197 2717 (paper)'",
        instanceWithIdentifiers(isbn("1-86-197 2717 (paper)")), List.of("1861972717 (paper)"))
    );
  }

  private static Identifiers isbn(String value) {
    return identifier(ISBN_IDENTIFIER_TYPE_ID, value);
  }

  private static Identifiers invalidIsbn(String value) {
    return identifier(INVALID_ISBN_IDENTIFIER_TYPE_ID, value);
  }

  private void mockFetchReferenceData(Set<String> referenceData) {
    when(referenceDataService.fetchReferenceData(IDENTIFIER_TYPES, isbnProcessor.getIdentifierNames()))
      .thenReturn(referenceData);
  }
}
