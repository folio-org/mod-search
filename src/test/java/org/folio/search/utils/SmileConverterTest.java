package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.support.utils.TestUtils.SMILE_MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;
import org.apache.commons.lang3.SerializationException;
import org.folio.spring.testing.type.UnitTest;
import org.folio.support.utils.TestUtils.NonSerializableByJacksonClass;
import org.folio.support.utils.TestUtils.TestClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.core.common.bytes.BytesArray;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SmileConverterTest {

  private static final String FIELD_VALUE = "value";

  @Spy
  private final SmileMapper mapper = SMILE_MAPPER;
  @InjectMocks
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
