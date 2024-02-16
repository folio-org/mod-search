package org.folio.search.service.setter;

import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.folio.search.integration.ClassificationTypeHelper;
import org.folio.search.model.index.ClassificationResource;
import org.folio.search.model.types.ClassificationType;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ClassificationShelvingOrderProcessorTest {

  private @Mock ClassificationTypeHelper classificationTypeHelper;

  private @InjectMocks ClassificationShelvingOrderProcessor processor;

  @Test
  void getFieldValue_ReturnsCalculatedShelfKey_ForLc() {
    // Arrange
    var resource = new ClassificationResource("LC", "12345", "NL 12.4N", emptySet());

    when(classificationTypeHelper.getClassificationTypeMap()).thenReturn(Map.of("12345", ClassificationType.LC));

    // Act
    String result = processor.getFieldValue(resource);

    // Assert
    assertEquals("NL 212.4 _N", result);
  }

  @Test
  void getFieldValue_ReturnsNormalizedShelfKey_ForDefault() {
    // Arrange
    var resource = new ClassificationResource("Default", "1", "S df123.dd", emptySet());

    when(classificationTypeHelper.getClassificationTypeMap()).thenReturn(new HashMap<>());

    // Act
    String result = processor.getFieldValue(resource);

    // Assert
    assertEquals("S DF123.DD", result);
  }
}
