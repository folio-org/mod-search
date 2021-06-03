package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.service.setter.instance.IssnProcessor.ISSN_IDENTIFIER_NAMES;
import static org.folio.search.utils.TestConstants.INVALID_ISSN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestConstants.ISBN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestConstants.ISSN_IDENTIFIER_TYPE_ID;
import static org.folio.search.utils.TestUtils.identifier;
import static org.folio.search.utils.TestUtils.instanceWithIdentifiers;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceIdentifiers;
import org.folio.search.repository.cache.InstanceIdentifierTypeCache;
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
class IssnProcessorTest {

  @InjectMocks private IssnProcessor issnProcessor;
  @Mock private InstanceIdentifierTypeCache identifierTypeCache;

  @MethodSource("issnDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance instance, List<String> expected) {
    if (CollectionUtils.isNotEmpty(instance.getIdentifiers())) {
      var issnIdentifierId = Set.of(ISSN_IDENTIFIER_TYPE_ID, INVALID_ISSN_IDENTIFIER_TYPE_ID);
      when(identifierTypeCache.fetchIdentifierIds(ISSN_IDENTIFIER_NAMES)).thenReturn(issnIdentifierId);
    }

    var actual = issnProcessor.getFieldValue(instance);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  private static Stream<Arguments> issnDataProvider() {
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("issn identifier=null", instanceWithIdentifiers(issn(null)), emptyList()),
      arguments("invalid issn identifier=null", instanceWithIdentifiers(invalidIssn(null)), emptyList()),
      arguments("issn identifier='0317-8471'", instanceWithIdentifiers(issn("0317-8471")), List.of("0317-8471")),
      arguments("issn identifier='0317-8471'",
        instanceWithIdentifiers(issn("0317-8471"), issn("0317-8471")), List.of("0317-8471")),
      arguments("issn identifier=' 0317-8471 '",
        instanceWithIdentifiers(issn(" 0317-8471 ")), List.of("0317-8471")),
      arguments("invalid issn identifier=' 0317-8471 '",
        instanceWithIdentifiers(invalidIssn(" 0317-8471 ")), List.of("0317-8471")),
      arguments("invalid issn identifier='03178471 '",
        instanceWithIdentifiers(invalidIssn("03178471 ")), List.of("03178471")),
      arguments("isbn identifier='047144250X'",
        instanceWithIdentifiers(identifier(ISBN_IDENTIFIER_TYPE_ID, "047144250X")), emptyList())
    );
  }

  private static InstanceIdentifiers issn(String value) {
    return identifier(ISSN_IDENTIFIER_TYPE_ID, value);
  }

  private static InstanceIdentifiers invalidIssn(String value) {
    return identifier(INVALID_ISSN_IDENTIFIER_TYPE_ID, value);
  }
}
