package org.folio.search.service.setter.instance;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.ALTERNATIVE_TITLE_TYPES;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.AlternativeTitle;
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
class UniformTitleProcessorTest {

  private static final String UNIFORM_TITLE_TYPE_ID = randomId();
  private static final String SIMPLE_TITLE_TYPE_ID = randomId();
  private static final List<String> UNIFORM_TITLES = singletonList("Uniform Title");

  @InjectMocks
  private UniformTitleProcessor uniformTitleProcessor;
  @Mock
  private ReferenceDataService referenceDataService;

  private static Stream<Arguments> testDataProvider() {
    var alternativeTitle = "title";
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("alternative title without id", instance(alternativeTitle(null, alternativeTitle)), emptyList()),
      arguments("non uniform alternative title ",
        instance(alternativeTitle(SIMPLE_TITLE_TYPE_ID, alternativeTitle)), emptyList()),
      arguments("uniform alternative title ",
        instance(alternativeTitle(UNIFORM_TITLE_TYPE_ID, alternativeTitle)), List.of(alternativeTitle))
    );
  }

  private static AlternativeTitle alternativeTitle(String id, String value) {
    return new AlternativeTitle().alternativeTitleTypeId(id).alternativeTitle(value);
  }

  private static Instance instance(AlternativeTitle... alternativeTitles) {
    return new Instance().alternativeTitles(alternativeTitles != null ? asList(alternativeTitles) : null);
  }

  @MethodSource("testDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance instance, List<String> expected) {
    when(referenceDataService.fetchReferenceData(ALTERNATIVE_TITLE_TYPES, CqlQueryParam.NAME, UNIFORM_TITLES))
      .thenReturn(singleton(UNIFORM_TITLE_TYPE_ID));
    var actual = uniformTitleProcessor.getFieldValue(instance);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  @Test
  void getFieldValue_negative() {
    when(referenceDataService.fetchReferenceData(ALTERNATIVE_TITLE_TYPES, CqlQueryParam.NAME, UNIFORM_TITLES))
      .thenReturn(emptySet());
    var actual = uniformTitleProcessor.getFieldValue(new Instance().id(RESOURCE_ID)
      .alternativeTitles(List.of(alternativeTitle(UNIFORM_TITLE_TYPE_ID, "value"))));
    assertThat(actual).isEmpty();
  }
}
