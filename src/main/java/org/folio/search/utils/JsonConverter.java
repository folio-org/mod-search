package org.folio.search.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationException;
import org.springframework.stereotype.Component;

/**
 * A Spring component for serialization and deserialization operations basing on jackson
 * objectMapper.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonConverter {

  public static final String SERIALIZATION_ERROR_MSG_TEMPLATE =
    "Failed to serialize value [message: %s]";
  public static final String DESERIALIZATION_ERROR_MSG_TEMPLATE =
    "Failed to deserialize value [value: {}]";

  private final ObjectMapper objectMapper;

  /**
   * Converts {@link String} value as {@link T} class value.
   *
   * @param value json value as {@link String} object
   * @param type target class to conversion value from json
   * @param <T> generic type for class.
   * @return converted {@link T} from json value
   */
  @SuppressWarnings("unused")
  public <T> T fromJson(String value, Class<T> type) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.readValue(value, type);
    } catch (JsonProcessingException e) {
      log.warn(DESERIALIZATION_ERROR_MSG_TEMPLATE, value, e);
      throw deserializationException(value, e);
    }
  }

  /**
   * Converts {@link String} value as {@link T} class value.
   *
   * @param value json value as {@link String} object
   * @param type target class for conversion value from json
   * @param <T> generic type for class.
   * @return converted {@link T} from json value
   */
  @SuppressWarnings("unused")
  public <T> T fromJson(String value, TypeReference<T> type) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.readValue(value, type);
    } catch (JsonProcessingException e) {
      log.warn(DESERIALIZATION_ERROR_MSG_TEMPLATE, value, e);
      throw deserializationException(value, e);
    }
  }

  /**
   * Converts {@link InputStream} value as {@link T} class value.
   *
   * @param inputStream json value stream as {@link InputStream} object
   * @param type target class to conversion value from json
   * @param <T> generic type for class.
   * @return converted {@link T} object from input stream
   */
  public <T> T readJson(InputStream inputStream, Class<T> type) {
    if (inputStream == null) {
      return null;
    }
    try {
      return objectMapper.readValue(inputStream, type);
    } catch (IOException e) {
      throw deserializationException(inputStream.toString(), e);
    }
  }

  /**
   * Converts {@link InputStream} value as {@link T} class value.
   *
   * @param inputStream json value stream as {@link InputStream} object
   * @param type target class to conversion value from json
   * @param <T> generic type for class.
   * @return converted {@link T} object from input stream
   */
  public <T> T readJson(InputStream inputStream, TypeReference<T> type) {
    if (inputStream == null) {
      return null;
    }
    try {
      return objectMapper.readValue(inputStream, type);
    } catch (IOException e) {
      throw deserializationException(inputStream.toString(), e);
    }
  }

  /**
   * Reads {@link String} value as jackson {@link JsonNode} object.
   *
   * @param value json value stream as {@link InputStream} object
   * @return converted {@link JsonNode} object from input stream
   */
  public JsonNode asJsonTree(String value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.readTree(value);
    } catch (IOException e) {
      throw deserializationException(value, e);
    }
  }

  /**
   * Reads {@link InputStream} value as jackson {@link JsonNode} object.
   *
   * @param inputStream json value stream as {@link InputStream} object
   * @return converted {@link JsonNode} object from input stream
   */
  public JsonNode asJsonTree(InputStream inputStream) {
    if (inputStream == null) {
      return null;
    }
    try {
      return objectMapper.readTree(inputStream);
    } catch (IOException e) {
      throw deserializationException(inputStream.toString(), e);
    }
  }

  /**
   * Reads {@link String} value as jackson {@link JsonNode} object.
   *
   * @param value json value stream as {@link InputStream} object
   * @return converted {@link JsonNode} object from input stream
   */
  public JsonNode toJsonTree(Object value) {
    if (value == null) {
      return null;
    }
    return objectMapper.valueToTree(value);
  }

  /**
   * Validates string if it's correct JSON value or not.
   *
   * @param value JSON string to validate
   * @return true if string value contains valid JSON, false - otherwise.
   */
  public boolean isValidJsonString(String value) {
    if (value == null) {
      return false;
    }
    try {
      objectMapper.readTree(value);
    } catch (JsonProcessingException e) {
      return false;
    }
    return true;
  }

  /**
   * Converts passed {@link Object} value to json string.
   *
   * @param value value to convert
   * @return json value as {@link String}.
   */
  public String toJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new SerializationException(String.format(
        SERIALIZATION_ERROR_MSG_TEMPLATE, e.getMessage()));
    }
  }

  private static RuntimeException deserializationException(String value, Throwable e) {
    log.warn(DESERIALIZATION_ERROR_MSG_TEMPLATE, value, e);
    return new SerializationException(String.format(
      "Failed to deserialize value [value: %s, message: %s]", value, e.getMessage()));
  }
}
