package org.folio.search.cql.flat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.folio.search.cql.flat.FieldLevelClassifier.ResourceLevel;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@UnitTest
class FieldLevelClassifierTest {

  private FieldLevelClassifier classifier;

  @BeforeEach
  void setUp() {
    classifier = new FieldLevelClassifier(new ObjectMapper());
  }

  @ParameterizedTest
  @CsvSource({
    "id, INSTANCE",
    "hrid, INSTANCE",
    "title, INSTANCE",
    "source, INSTANCE",
    "permanentLocationId, HOLDING",
    "callNumber, HOLDING",
    "barcode, ITEM",
    "effectiveLocationId, ITEM",
    "holdings.id, HOLDING",
    "holdings.hrid, HOLDING",
    "items.id, ITEM",
    "items.hrid, ITEM",
    "item.barcode, ITEM"
  })
  void classifyResolvesFieldsToExpectedLevel(String field, ResourceLevel expected) {
    assertThat(classifier.classify(field)).isEqualTo(expected);
  }

  @Test
  void unknownFieldDefaultsToInstance() {
    assertThat(classifier.classify("some.unknown.field")).isEqualTo(ResourceLevel.INSTANCE);
  }

  @ParameterizedTest
  @CsvSource({
    "id, instance.id",
    "hrid, instance.hrid",
    "title, instance.title",
    "holdings.hrid, holding.hrid",
    "items.id, item.id",
    "item.barcode, item.barcode",
    "barcode, item.barcode",
    "permanentLocationId, holding.permanentLocationId"
  })
  void normalizeFieldAddsCorrectNamespace(String field, String expected) {
    assertThat(classifier.normalizeField(field)).isEqualTo(expected);
  }
}
