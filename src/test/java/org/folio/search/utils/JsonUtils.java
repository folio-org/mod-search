package org.folio.search.utils;

import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.List;
import java.util.Map;

public class JsonUtils {

  public static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS_TYPE_REFERENCE = new TypeReference<>() { };

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
      case String s -> TextNode.valueOf(s);
      case Float v -> DoubleNode.valueOf(v);
      case Double v -> DoubleNode.valueOf(v);
      case Integer i -> IntNode.valueOf(i);
      case Long l -> LongNode.valueOf(l);
      case Boolean b -> BooleanNode.valueOf(b);
      default -> jsonObject();
    };

  }
}
