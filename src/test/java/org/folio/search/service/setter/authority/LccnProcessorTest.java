package org.folio.search.service.setter.authority;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.IDENTIFIER_TYPES;
import static org.folio.search.utils.TestConstants.LCCN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestUtils.authorityWithIdentifiers;
import static org.folio.search.utils.TestUtils.identifier;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Identifiers;
import org.folio.search.integration.ReferenceDataService;
import org.folio.spring.test.type.UnitTest;
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
class LccnProcessorTest {

  @InjectMocks
  private LccnProcessor lccnProcessor;
  @Mock
  private ReferenceDataService referenceDataService;

  @MethodSource("lccnDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] authority with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Authority authority,
                                   List<String> expected) {
    if (CollectionUtils.isNotEmpty(authority.getIdentifiers())) {
      var identifiers = Set.of(LCCN_IDENTIFIER_TYPE_ID);
      mockFetchReferenceData(identifiers);
    }

    var actual = lccnProcessor.getFieldValue(authority);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  @Test
  void getFieldValue_negative_failedToLoadReferenceData() {
    mockFetchReferenceData(emptySet());
    var actual = lccnProcessor.getFieldValue(authorityWithIdentifiers(lccn("123456")));
    assertThat(actual).isEmpty();
  }

  private static Stream<Arguments> lccnDataProvider() {
    return Stream.of(
      arguments("all empty fields", new Authority(), emptyList()),
      arguments("lccn identifier=null", authorityWithIdentifiers(lccn(null)), emptyList()),
      arguments("lccn identifier='3745-1086'", authorityWithIdentifiers(lccn("3745-1086")), List.of("3745-1086")),
      arguments("lccn identifier='3745-1086'",
        authorityWithIdentifiers(lccn("3745-1086"), lccn("3745-1086")), List.of("3745-1086")),
      arguments("lccn identifier=' 3745-1086 '",
        authorityWithIdentifiers(lccn(" 3745-1086 ")), List.of("3745-1086"))
    );
  }

  private static Identifiers lccn(String value) {
    return identifier(LCCN_IDENTIFIER_TYPE_ID, value);
  }

  private void mockFetchReferenceData(Set<String> referenceData) {
    when(referenceDataService.fetchReferenceData(IDENTIFIER_TYPES, lccnProcessor.getIdentifierNames()))
      .thenReturn(referenceData);
  }
}
