package org.folio.search.service.setter.authority;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.LCCN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestUtils.authorityIdentifier;
import static org.folio.search.utils.TestUtils.authorityWithIdentifiers;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthorityIdentifiers;
import org.folio.search.integration.InstanceReferenceDataService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
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
  private InstanceReferenceDataService referenceDataService;

  @MethodSource("lccnDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] authority with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Authority authority,
                                   List<String> expected) {
    if (CollectionUtils.isNotEmpty(authority.getIdentifiers())) {
      var lccnIdentifierId = Set.of(LCCN_IDENTIFIER_TYPE_ID);
      when(referenceDataService.fetchIdentifierIds(lccnProcessor.getIdentifierNames())).thenReturn(lccnIdentifierId);
    }

    var actual = lccnProcessor.getFieldValue(authority);
    assertThat(actual).containsExactlyElementsOf(expected);
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

  private static AuthorityIdentifiers lccn(String value) {
    return authorityIdentifier(LCCN_IDENTIFIER_TYPE_ID, value);
  }
}
