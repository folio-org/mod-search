package org.folio.search.service.setter.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ShelvingOrderAlgorithmType.DEFAULT;
import static org.folio.search.domain.dto.ShelvingOrderAlgorithmType.DEWEY;
import static org.folio.search.domain.dto.ShelvingOrderAlgorithmType.LC;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.model.index.ClassificationResource;
import org.folio.search.utils.ShelvingOrderCalculationHelper;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

@UnitTest
class ClassificationShelvingOrderFieldProcessorTest {

  private static final String INPUT = "TestNum";
  private static final String OUTPUT = "ResultNum";
  private ClassificationResource eventBody;

  public static Stream<Arguments> testData() {
    return Stream.of(
      Arguments.arguments(new DefaultClassificationShelvingOrderFieldProcessor(), DEFAULT),
      Arguments.arguments(new DeweyClassificationShelvingOrderFieldProcessor(), DEWEY),
      Arguments.arguments(new LcClassificationShelvingOrderFieldProcessor(), LC)
    );
  }

  @BeforeEach
  void setUp() {
    eventBody = mock(ClassificationResource.class);
    when(eventBody.number()).thenReturn(INPUT);
  }

  @MethodSource("testData")
  @ParameterizedTest
  void testDefaultClassificationShelvingOrderFieldProcessor(ClassificationShelvingOrderFieldProcessor processor,
                                                            ShelvingOrderAlgorithmType algorithmType) {
    try (var helper = Mockito.mockStatic(ShelvingOrderCalculationHelper.class)) {
      helper.when(() -> ShelvingOrderCalculationHelper.calculate(INPUT, algorithmType)).thenReturn(OUTPUT);

      var fieldValue = processor.getFieldValue(eventBody);
      assertThat(fieldValue).isEqualTo(OUTPUT);
    }
  }

}
