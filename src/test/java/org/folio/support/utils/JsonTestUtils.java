package org.folio.support.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import lombok.SneakyThrows;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.LongNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

public class JsonTestUtils {

  public static final JsonMapper OBJECT_MAPPER = JsonMapper.builder()
    .changeDefaultPropertyInclusion(value -> value.withContentInclusion(JsonInclude.Include.NON_NULL))
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
    .build();

  public static ObjectNode jsonObject() {
    return OBJECT_MAPPER.createObjectNode();
  }

  public static <T> ObjectNode jsonObject(String k1, T v1, Object... pairs) {
    var objectNode = jsonObject();
    objectNode.set(k1, jsonNode(v1));

    for (int i = 0; i < pairs.length; i += 2) {
      String key = (String) pairs[i];
      Object value = pairs[i + 1];
      objectNode.set(key, jsonNode(value));
    }

    return objectNode;
  }

  public static ArrayNode jsonArray(Object... values) {
    var arrayNode = OBJECT_MAPPER.createArrayNode();
    for (Object value : values) {
      arrayNode.add(jsonNode(value));
    }
    return arrayNode;
  }

  public static JsonNode jsonNode(Object value) {
    return switch (value) {
      case null -> NullNode.instance;
      case JsonNode jsonNode -> jsonNode;
      case String s -> StringNode.valueOf(s);
      case Float v -> DoubleNode.valueOf(v);
      case Double v -> DoubleNode.valueOf(v);
      case Integer i -> IntNode.valueOf(i);
      case Long l -> LongNode.valueOf(l);
      case Boolean b -> BooleanNode.valueOf(b);
      default -> jsonObject();
    };
  }

  @SneakyThrows
  public static String asJsonString(Object value) {
    return OBJECT_MAPPER.writeValueAsString(value);
  }

  @SneakyThrows
  public static <T> T readJsonFromFile(String path, Class<T> type) {
    try (var resource = TestUtils.class.getResourceAsStream(path)) {
      return OBJECT_MAPPER.readValue(resource, type);
    }
  }

  @SneakyThrows
  public static <T> T readJsonFromFile(String path, TypeReference<T> type) {
    try (var resource = TestUtils.class.getResourceAsStream(path)) {
      return OBJECT_MAPPER.readValue(resource, type);
    }
  }

  public static Map<String, Object> readJsonFromFileAsMap(String path) {
    return readJsonFromFile(path, new TypeReference<>() { });
  }

  @SneakyThrows
  public static <T> T parseResponse(ResultActions result, Class<T> type) {
    return OBJECT_MAPPER.readValue(result.andReturn().getResponse().getContentAsString(), type);
  }

  @SneakyThrows
  public static <T> T parseResponse(ResultActions result, TypeReference<T> type) {
    return OBJECT_MAPPER.readValue(result.andReturn().getResponse().getContentAsString(), type);
  }

  public static Map<String, Object> toMap(Object value) {
    return OBJECT_MAPPER.convertValue(value, new TypeReference<>() { });
  }

  public static <T> T toObject(Map<String, Object> map, Class<T> type) {
    return OBJECT_MAPPER.convertValue(map, type);
  }
}
