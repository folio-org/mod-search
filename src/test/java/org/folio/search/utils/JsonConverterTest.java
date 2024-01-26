package org.folio.search.utils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.JsonConverter.MAP_TYPE_REFERENCE;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.apache.commons.lang3.SerializationException;
import org.folio.search.utils.TestUtils.NonSerializableByJacksonClass;
import org.folio.search.utils.TestUtils.TestClass;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.core.common.bytes.BytesArray;

@UnitTest
@ExtendWith(MockitoExtension.class)
class JsonConverterTest {

  private static final String JSON_BODY = "{\"field\":\"value\"}";
  private static final BytesArray JSON_BYTES_BODY = new BytesArray(JSON_BODY);
  private static final String WRONG_JSON_BODY = "{\"field\":value}";
  private static final String FIELD_VALUE = "value";

  @Spy
  private final ObjectMapper objectMapper = OBJECT_MAPPER;
  @InjectMocks
  private JsonConverter jsonConverter;

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
  void toJson_negative_throwsException() {
    var value = new NonSerializableByJacksonClass();
    assertThatThrownBy(() -> jsonConverter.toJson(value))
      .isInstanceOf(SerializationException.class)
      .hasMessageContaining("Failed to serialize value");
  }

  @Test
  void toJsonBytes_positive() throws JsonProcessingException {
    var actual = jsonConverter.toJsonBytes(TestClass.of(FIELD_VALUE));
    assertThat(actual).isEqualTo(JSON_BYTES_BODY);

    verify(objectMapper).writeValueAsString(TestClass.of(FIELD_VALUE));
  }

  @Test
  void toJsonBytes_positive_nullValue() {
    var actual = jsonConverter.toJsonBytes(null);
    assertThat(actual).isNull();
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
  void fromJsonForClass_negative_throwsException() {
    assertThatThrownBy(() -> jsonConverter.fromJson(WRONG_JSON_BODY, TestClass.class))
      .isInstanceOf(SerializationException.class)
      .hasMessageContaining("Failed to deserialize value");
  }

  @Test
  void fromJsonForType_positive() throws JsonProcessingException {
    var actual = jsonConverter.fromJson(JSON_BODY, MAP_TYPE_REFERENCE);
    assertThat(actual).isEqualTo(Map.of("field", FIELD_VALUE));
    verify(objectMapper).readValue(JSON_BODY, MAP_TYPE_REFERENCE);
  }

  @Test
  void fromJsonForType_positive_null() {
    var actual = jsonConverter.fromJson(null, MAP_TYPE_REFERENCE);
    assertThat(actual).isNull();
  }

  @Test
  void fromJsonForType_negative_throwsException() {
    assertThatThrownBy(() -> jsonConverter.fromJson(WRONG_JSON_BODY, MAP_TYPE_REFERENCE))
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
  void fromJsonInputStreamForClass_negative_throwsException() {
    InputStream is = new ByteArrayInputStream(WRONG_JSON_BODY.getBytes(UTF_8));
    assertThatThrownBy(() -> jsonConverter.readJson(is, TestClass.class))
      .isInstanceOf(SerializationException.class)
      .hasMessageContaining("Failed to deserialize value");
  }

  @Test
  void fromJsonInputStreamForType_positive() throws IOException {
    InputStream is = new ByteArrayInputStream(JSON_BODY.getBytes(UTF_8));
    var actual = jsonConverter.readJson(is, MAP_TYPE_REFERENCE);
    assertThat(actual).isEqualTo(Map.of("field", FIELD_VALUE));
    verify(objectMapper).readValue(is, MAP_TYPE_REFERENCE);
  }

  @Test
  void fromJsonInputStreamForType_positive_null() {
    var actual = jsonConverter.readJson(null, MAP_TYPE_REFERENCE);
    assertThat(actual).isNull();
  }

  @Test
  void fromJsonInputStreamForType_negative_throwsException() {
    InputStream is = new ByteArrayInputStream(WRONG_JSON_BODY.getBytes(UTF_8));
    assertThatThrownBy(() -> jsonConverter.readJson(is, MAP_TYPE_REFERENCE))
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
  void asJsonTree_string_negative_throwsException() {
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
  void asJsonTree_inputStream_negative_throwsException() {
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

  @Test
  void convert_positive_class() {
    var actual = jsonConverter.convert(mapOf("field", FIELD_VALUE), TestClass.class);
    assertThat(actual).isEqualTo(TestClass.of(FIELD_VALUE));
  }

  @Test
  void convert_positive_classNullValue() {
    var actual = jsonConverter.convert(null, TestClass.class);
    assertThat(actual).isNull();
  }

  @Test
  void convert_positive_typeReference() {
    var actual = jsonConverter.convert(mapOf("field", FIELD_VALUE), new TypeReference<TestClass>() { });
    assertThat(actual).isEqualTo(TestClass.of(FIELD_VALUE));
  }

  @Test
  void convert_positive_typeReferenceNullValue() {
    var actual = jsonConverter.convert(null, new TypeReference<TestClass>() { });
    assertThat(actual).isNull();
  }
}
