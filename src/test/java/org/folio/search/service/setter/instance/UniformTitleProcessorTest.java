package org.folio.search.service.setter.instance;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceAlternativeTitles;
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
class UniformTitleProcessorTest {

  private static final String UNIFORM_TITLE_TYPE_ID = randomId();
  private static final String SIMPLE_TITLE_TYPE_ID = randomId();

  @InjectMocks private UniformTitleProcessor uniformTitleProcessor;
  @Mock private InstanceReferenceDataService referenceDataService;

  @MethodSource("testDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance instance, List<String> expected) {
    var uniformTitles = singletonList("Uniform Title");
    when(referenceDataService.fetchAlternativeTitleIds(uniformTitles)).thenReturn(singleton(UNIFORM_TITLE_TYPE_ID));
    var actual = uniformTitleProcessor.getFieldValue(instance);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

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

  private static InstanceAlternativeTitles alternativeTitle(String id, String value) {
    return new InstanceAlternativeTitles().alternativeTitleTypeId(id).alternativeTitle(value);
  }

  private static Instance instance(InstanceAlternativeTitles... alternativeTitles) {
    return new Instance().alternativeTitles(alternativeTitles != null ? asList(alternativeTitles) : null);
  }
}
