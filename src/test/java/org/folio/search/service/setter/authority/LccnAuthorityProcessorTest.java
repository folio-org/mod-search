package org.folio.search.service.setter.authority;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.IDENTIFIER_TYPES;
import static org.folio.search.utils.TestConstants.LCCN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestUtils.authorityWithIdentifiers;
import static org.folio.search.utils.TestUtils.identifier;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Identifiers;
import org.folio.search.integration.ReferenceDataService;
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
class LccnAuthorityProcessorTest {

  @InjectMocks
  private LccnAuthorityProcessor lccnProcessor;
  @Mock
  private ReferenceDataService referenceDataService;

  @MethodSource("lccnDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] authority with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Authority authority, Set<String> expected) {
    if (CollectionUtils.isNotEmpty(authority.getIdentifiers())) {
      var identifiers = Set.of(LCCN_IDENTIFIER_TYPE_ID);
      mockFetchReferenceData(identifiers);
    }

    var actual = lccnProcessor.getFieldValue(authority);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void getFieldValue_negative_failedToLoadReferenceData() {
    mockFetchReferenceData(emptySet());
    var actual = lccnProcessor.getFieldValue(authorityWithIdentifiers(lccn("123456")));
    assertThat(actual).isEmpty();
  }

  private static Stream<Arguments> lccnDataProvider() {
    return Stream.of(
      arguments("all empty fields", new Authority(), emptySet()),
      arguments("lccn identifier=null", authorityWithIdentifiers(lccn(null), lccn("  ")), emptySet()),
      arguments("lccn identifier='  nbc  79021425 '",
        authorityWithIdentifiers(lccn("  nbc  79021425 ")), Set.of("nbc79021425", "79021425")),
      arguments("lccn identifier='79021425'",
        authorityWithIdentifiers(lccn("79021425"), lccn("79021425")), Set.of("79021425")),
      arguments("lccn identifier='N79021425'",
        authorityWithIdentifiers(lccn("N79021425")), Set.of("n79021425", "79021425")),
      arguments("lccn identifier='*79021425*'",
        authorityWithIdentifiers(lccn("*1425"), lccn("7902*")), Set.of("*1425", "1425", "7902*", "7902"))
    );
  }

  private static Identifiers lccn(String value) {
    return identifier(LCCN_IDENTIFIER_TYPE_ID, value);
  }

  private void mockFetchReferenceData(Set<String> referenceData) {
    when(referenceDataService.fetchReferenceData(IDENTIFIER_TYPES, CqlQueryParam.NAME,
      lccnProcessor.getIdentifierNames()))
      .thenReturn(referenceData);
  }
}
