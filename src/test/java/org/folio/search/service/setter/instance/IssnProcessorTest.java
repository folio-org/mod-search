package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.IDENTIFIER_TYPES;
import static org.folio.support.TestConstants.INVALID_ISSN_IDENTIFIER_TYPE_ID;
import static org.folio.support.TestConstants.ISBN_IDENTIFIER_TYPE_ID;
import static org.folio.support.TestConstants.ISSN_IDENTIFIER_TYPE_ID;
import static org.folio.support.TestConstants.LINKING_ISSN_IDENTIFIER_TYPE_ID;
import static org.folio.support.utils.TestUtils.identifier;
import static org.folio.support.utils.TestUtils.instanceWithIdentifiers;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.domain.dto.Identifier;
import org.folio.search.domain.dto.Instance;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.search.model.client.CqlQueryParam;
import org.folio.spring.testing.type.UnitTest;
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
class IssnProcessorTest {

  @InjectMocks
  private IssnProcessor issnProcessor;
  @Mock
  private ReferenceDataService referenceDataService;

  @MethodSource("issnDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance instance, List<String> expected) {
    if (CollectionUtils.isNotEmpty(instance.getIdentifiers())) {
      var identifiers = Set.of(ISSN_IDENTIFIER_TYPE_ID,
        INVALID_ISSN_IDENTIFIER_TYPE_ID,
        LINKING_ISSN_IDENTIFIER_TYPE_ID);
      mockFetchReferenceData(identifiers);
    }

    var actual = issnProcessor.getFieldValue(instance);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  @Test
  void getFieldValue_negative_failedToLoadReferenceData() {
    mockFetchReferenceData(emptySet());
    var actual = issnProcessor.getFieldValue(instanceWithIdentifiers(issn("123456")));
    assertThat(actual).isEmpty();
  }

  private static Stream<Arguments> issnDataProvider() {
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("issn identifier=null", instanceWithIdentifiers(issn(null)), emptyList()),
      arguments("invalid issn identifier=null", instanceWithIdentifiers(invalidIssn(null)), emptyList()),
      arguments("linking issn identifier=null", instanceWithIdentifiers(invalidIssn(null)), emptyList()),
      arguments("issn identifier='0317-8471'", instanceWithIdentifiers(issn("0317-8471")), List.of("0317-8471")),
      arguments("issn identifier='0317-8471'",
        instanceWithIdentifiers(issn("0317-8471"), issn("0317-8471")), List.of("0317-8471")),
      arguments("issn identifier=' 0317-8471 '",
        instanceWithIdentifiers(issn(" 0317-8471 ")), List.of("0317-8471")),
      arguments("invalid issn identifier=' 0317-8471 '",
        instanceWithIdentifiers(invalidIssn(" 0317-8471 ")), List.of("0317-8471")),
      arguments("invalid issn identifier='03178471 '",
        instanceWithIdentifiers(invalidIssn("03178471 ")), List.of("03178471")),
      arguments("linking issn identifier=' 0317-8471 '",
        instanceWithIdentifiers(linkingIssn(" 0317-8471 ")), List.of("0317-8471")),
      arguments("linking issn identifier='03178471 '",
        instanceWithIdentifiers(linkingIssn("03178471 ")), List.of("03178471")),
      arguments("isbn identifier='047144250X'",
        instanceWithIdentifiers(identifier(ISBN_IDENTIFIER_TYPE_ID, "047144250X")), emptyList())
    );
  }

  private static Identifier issn(String value) {
    return identifier(ISSN_IDENTIFIER_TYPE_ID, value);
  }

  private static Identifier invalidIssn(String value) {
    return identifier(INVALID_ISSN_IDENTIFIER_TYPE_ID, value);
  }

  private static Identifier linkingIssn(String value) {
    return identifier(LINKING_ISSN_IDENTIFIER_TYPE_ID, value);
  }

  private void mockFetchReferenceData(Set<String> referenceData) {
    when(referenceDataService.fetchReferenceData(IDENTIFIER_TYPES, CqlQueryParam.NAME,
      issnProcessor.getIdentifierNames()))
      .thenReturn(referenceData);
  }
}
