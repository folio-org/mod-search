package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestUtils.SMILE_MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.SerializationException;
import org.folio.search.utils.TestUtils.NonSerializableByJacksonClass;
import org.folio.search.utils.TestUtils.TestClass;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.core.common.bytes.BytesArray;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SmileConverterTest {

  private static final String FIELD_VALUE = "value";

  @Spy
  private SmileConverter smileConverter;

  @Test
  void toSmile_positive() throws JsonProcessingException {
    var object = TestClass.of(FIELD_VALUE);
    var expected = new BytesArray(SMILE_MAPPER.writeValueAsBytes(object));
    var actual = smileConverter.toSmile(object);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void toJson_positive_nullValue() {
    var actual = smileConverter.toSmile(null);
    assertThat(actual).isNull();
  }

  @Test
  void toJson_negative_throwsException() {
    var value = new NonSerializableByJacksonClass();
    assertThatThrownBy(() -> smileConverter.toSmile(value))
      .isInstanceOf(SerializationException.class)
      .hasMessageContaining("Failed to serialize value");
  }
}
