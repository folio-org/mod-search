package org.folio.search.service.setter.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Set;
import org.folio.search.domain.dto.Classification;
import org.folio.search.domain.dto.Instance;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@UnitTest
class ClassificationNumberProcessorTest {

  private ClassificationNumberProcessor processor;
  private Instance instance;
  private Classification classification;

  @BeforeEach
  void setUp() {
    processor = new ClassificationNumberProcessor();
    instance = new Instance();
    classification = new Classification();
  }

  @Test
  void testClassificationNumberProcessed() {
    classification.setClassificationNumber("abc123#$%!!!");
    instance.setClassifications(Collections.singletonList(classification));

    Set<String> result = processor.getFieldValue(instance);

    assertEquals(Collections.singleton("abc123"), result);
  }

  @Test
  void testNullClassificationNumber() {
    instance.setClassifications(Collections.singletonList(classification));

    Set<String> result = processor.getFieldValue(instance);

    assertTrue(result.isEmpty());
  }

  @Test
  void testEmptyClassifications() {
    Set<String> result = processor.getFieldValue(instance);

    assertTrue(result.isEmpty());
  }
}
