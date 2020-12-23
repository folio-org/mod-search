package org.folio.search.utils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.SerializationException;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class JsonConverterTest {

  private static final String JSON_BODY = "{\"field\":\"value\"}";
  private static final String WRONG_JSON_BODY = "{\"field\":value}";
  private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};
  private static final String FIELD_VALUE = "value";

  @InjectMocks private JsonConverter jsonConverter;
  @Spy private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void toJson_positive() throws JsonProcessingException {
    var actual = jsonConverter.toJson(TestClass.of(FIELD_VALUE));
    assertThat(actual).isEqualTo(JSON_BODY);

    verify(objectMapper).writeValueAsString(TestClass.of(FIELD_VALUE));
  }

  @Test
  void toJson_positive_nullValue() {
    var actual = jsonConverter.toJson(null);
    assertThat(actual).isNull();
  }

  @Test
  void toJson_negative_throwsIOException() {
    var value = new NonSerializableByJacksonClass();
    assertThatThrownBy(() -> jsonConverter.toJson(value))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("Failed to serialize value");
  }

  @Test
  void fromJsonForClass_positive() throws JsonProcessingException {
    var actual = jsonConverter.fromJson(JSON_BODY, TestClass.class);
    assertThat(actual).isEqualTo(TestClass.of(FIELD_VALUE));
    verify(objectMapper).readValue(JSON_BODY, TestClass.class);
  }

  @Test
  void fromJsonForClass_positive_null() {
    var actual = jsonConverter.fromJson(null, TestClass.class);
    assertThat(actual).isNull();
  }

  @Test
  void fromJsonForClass_negative_throwsIOException() {
    assertThatThrownBy(() -> jsonConverter.fromJson(WRONG_JSON_BODY, TestClass.class))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("Failed to deserialize value");
  }

  @Test
  void fromJsonForType_positive() throws JsonProcessingException {
    var actual = jsonConverter.fromJson(JSON_BODY, MAP_TYPE);
    assertThat(actual).isEqualTo(Map.of("field", FIELD_VALUE));
    verify(objectMapper).readValue(JSON_BODY, MAP_TYPE);
  }

  @Test
  void fromJsonForType_positive_null() {
    var actual = jsonConverter.fromJson(null, MAP_TYPE);
    assertThat(actual).isNull();
  }

  @Test
  void fromJsonForType_negative_throwsIOException() {
    assertThatThrownBy(() -> jsonConverter.fromJson(WRONG_JSON_BODY, MAP_TYPE))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("Failed to deserialize value");
  }

  @Test
  void fromJsonInputStreamForClass_positive() throws IOException {
    InputStream is = new ByteArrayInputStream(JSON_BODY.getBytes(UTF_8));
    var actual = jsonConverter.readJson(is, TestClass.class);
    assertThat(actual).isEqualTo(TestClass.of(FIELD_VALUE));
    verify(objectMapper).readValue(is, TestClass.class);
  }

  @Test
  void fromJsonInputStreamForClass_positive_null() {
    var actual = jsonConverter.readJson(null, TestClass.class);
    assertThat(actual).isNull();
  }

  @Test
  void fromJsonInputStreamForClass_negative_throwsIOException() {
    InputStream is = new ByteArrayInputStream(WRONG_JSON_BODY.getBytes(UTF_8));
    assertThatThrownBy(() -> jsonConverter.readJson(is, TestClass.class))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("Failed to deserialize value");
  }

  @Test
  void fromJsonInputStreamForType_positive() throws IOException {
    InputStream is = new ByteArrayInputStream(JSON_BODY.getBytes(UTF_8));
    var actual = jsonConverter.readJson(is, MAP_TYPE);
    assertThat(actual).isEqualTo(Map.of("field", FIELD_VALUE));
    verify(objectMapper).readValue(is, MAP_TYPE);
  }

  @Test
  void fromJsonInputStreamForType_positive_null() {
    var actual = jsonConverter.readJson(null, MAP_TYPE);
    assertThat(actual).isNull();
  }

  @Test
  void fromJsonInputStreamForType_negative_throwsIOException() {
    InputStream is = new ByteArrayInputStream(WRONG_JSON_BODY.getBytes(UTF_8));
    assertThatThrownBy(() -> jsonConverter.readJson(is, MAP_TYPE))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("Failed to deserialize value");
  }

  @Test
  void asJsonTree_string_positive() {
    var actual = jsonConverter.asJsonTree(JSON_BODY);
    assertThat(actual).isEqualTo(jsonObject("field", FIELD_VALUE));
  }

  @Test
  void asJsonTree_string_positive_null() {
    var actual = jsonConverter.asJsonTree((String) null);
    assertThat(actual).isNull();
  }

  @Test
  void asJsonTree_string_negative_throwsIOException() {
    assertThatThrownBy(() -> jsonConverter.asJsonTree(WRONG_JSON_BODY))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("Failed to deserialize value");
  }

  @Test
  void asJsonTree_inputStream_positive() {
    InputStream is = new ByteArrayInputStream(JSON_BODY.getBytes(UTF_8));
    var actual = jsonConverter.asJsonTree(is);
    assertThat(actual).isEqualTo(jsonObject("field", FIELD_VALUE));
  }

  @Test
  void asJsonTree_inputStream_positive_null() {
    var actual = jsonConverter.asJsonTree((InputStream) null);
    assertThat(actual).isNull();
  }

  @Test
  void asJsonTree_inputStream_negative_throwsIOException() {
    InputStream is = new ByteArrayInputStream(WRONG_JSON_BODY.getBytes(UTF_8));
    assertThatThrownBy(() -> jsonConverter.asJsonTree(is))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("Failed to deserialize value");
  }

  @Test
  void toJsonTree_positive() {
    var actual = jsonConverter.toJsonTree(Map.of("field", FIELD_VALUE));
    assertThat(actual).isEqualTo(jsonObject("field", FIELD_VALUE));
  }

  @Test
  void toJsonTree_positive_null() {
    var actual = jsonConverter.toJsonTree(null);
    assertThat(actual).isNull();
  }

  @Test
  void isValidJsonString_positive() {
    var actual = jsonConverter.isValidJsonString("{}");
    assertThat(actual).isTrue();
  }

  @Test
  void isValidJsonString_negative_nullValue() {
    var actual = jsonConverter.isValidJsonString(null);
    assertThat(actual).isFalse();
  }

  @Test
  void isValidJsonString_negative_invalidJsonValue() {
    var actual = jsonConverter.isValidJsonString("{123}");
    assertThat(actual).isFalse();
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(staticName = "of")
  private static class TestClass {

    private String field;
  }

  private static class NonSerializableByJacksonClass {

    private final NonSerializableByJacksonClass self = this;

    @SuppressWarnings("unused")
    public NonSerializableByJacksonClass getSelf() {
      return self;
    }
  }
}
