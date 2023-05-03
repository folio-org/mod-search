package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.IDENTIFIER_TYPES;
import static org.folio.search.utils.TestConstants.CANCELED_OCLC_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestConstants.OCLC_IDENTIFIER_TYPE_ID;
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
import org.folio.search.model.client.CqlQueryParam;
import org.folio.spring.test.type.UnitTest;
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
class OclcProcessorTest {

  @InjectMocks
  private OclcProcessor oclcProcessor;
  @Mock
  private ReferenceDataService referenceDataService;

  private static Stream<Arguments> oclcDataProvider() {
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("empty oclc identifier", instanceWithIdentifiers(oclc("")), emptyList()),
      arguments("blank oclc identifier", instanceWithIdentifiers(oclc("  ")), emptyList()),
      arguments("issn identifier='0747-0088'", instanceWithIdentifiers(identifier("issn", "0747-0088")), emptyList()),
      arguments("oclc identifier=null", instanceWithIdentifiers(oclc(null)), emptyList()),
      arguments("oclc identifier='(OCoLC)842877062'", instanceWithIdentifiers(oclc("(OCoLC)842877062")),
        List.of("842877062")),
      arguments("oclc identifier='OCoLC842877062'", instanceWithIdentifiers(oclc("OCoLC842877062")),
        List.of("842877062")),
      arguments("oclc identifier='(OCoLC)ocm842877062'", instanceWithIdentifiers(oclc("(OCoLC)ocm842877062")),
        List.of("842877062")),
      arguments("oclc identifier='(OCoLC)ocm842877062'", instanceWithIdentifiers(oclc("(OCoLC)ocm842877062")),
        List.of("842877062")),
      arguments("oclc identifier='ocm842877062'", instanceWithIdentifiers(oclc("ocm842877062")), List.of("842877062")),
      arguments("oclc identifier='0842877062'", instanceWithIdentifiers(oclc("0842877062")), List.of("842877062")),
      arguments("oclc identifier='842877062'", instanceWithIdentifiers(oclc("842877062")), List.of("842877062")),
      arguments("oclc identifier='(OCoLC)60710867'", instanceWithIdentifiers(oclc("(OCoLC)60710867")),
        List.of("60710867")),
      arguments("oclc identifier='(OCoLC)00060710867'", instanceWithIdentifiers(oclc("(OCoLC)00060710867")),
        List.of("60710867")),
      arguments("oclc identifier='OCoLC000060710867'", instanceWithIdentifiers(oclc("OCoLC000060710867")),
        List.of("60710867")),
      arguments("oclc identifier='ocm0012345 800630'", instanceWithIdentifiers(oclc("ocm0012345 800630")),
        List.of("12345-800630")),
      arguments("oclc identifier='0012345 800630'", instanceWithIdentifiers(oclc("0012345 800630")),
        List.of("12345-800630")),
      arguments("oclc identifier='12345 800630'", instanceWithIdentifiers(oclc("12345 800630")),
        List.of("12345-800630")),
      arguments("oclc identifier='ocm0012345'", instanceWithIdentifiers(oclc("ocm0012345")), List.of("12345")),
      arguments("oclc identifier='0012345'", instanceWithIdentifiers(oclc("0012345")), List.of("12345")),
      arguments("canceled oclc identifier='ocm   842877062  '",
        instanceWithIdentifiers(canceledOclc("ocm   842877062  ")),
        List.of("842877062")),
      arguments("canceled oclc identifier='  0842877062'", instanceWithIdentifiers(canceledOclc("  0842877062")),
        List.of("842877062")),
      arguments("canceled oclc identifier='0012345'", instanceWithIdentifiers(canceledOclc("0012345")), List.of(
        "12345"))
    );
  }

  private static Identifiers oclc(String value) {
    return identifier(OCLC_IDENTIFIER_TYPE_ID, value);
  }

  private static Identifiers canceledOclc(String value) {
    return identifier(CANCELED_OCLC_IDENTIFIER_TYPE_ID, value);
  }

  @MethodSource("oclcDataProvider")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance instance, List<String> expected) {
    if (CollectionUtils.isNotEmpty(instance.getIdentifiers())) {
      var identifiers = setOf(OCLC_IDENTIFIER_TYPE_ID, CANCELED_OCLC_IDENTIFIER_TYPE_ID);
      mockFetchReferenceData(identifiers);
    }

    var actual = oclcProcessor.getFieldValue(instance);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  @Test
  void getFieldValue_negative_failedToLoadReferenceData() {
    mockFetchReferenceData(emptySet());
    var actual = oclcProcessor.getFieldValue(instanceWithIdentifiers(oclc("123456")));
    assertThat(actual).isEmpty();
  }

  private void mockFetchReferenceData(Set<String> referenceData) {
    when(referenceDataService.fetchReferenceData(IDENTIFIER_TYPES, CqlQueryParam.NAME,
      oclcProcessor.getIdentifierNames()))
      .thenReturn(referenceData);
  }
}
