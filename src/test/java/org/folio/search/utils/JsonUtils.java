package org.folio.search.utils;

import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class JsonUtils {

  public static ObjectNode jsonObject() {
    return OBJECT_MAPPER.createObjectNode();
  }

  public static <T> ObjectNode jsonObject(String k1, T v1, Object ... pairs) {
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
    if (value == null) {
      return NullNode.instance;
    }

    if (value instanceof JsonNode) {
      return (JsonNode) value;
    }

    if (value instanceof String) {
      return TextNode.valueOf((String) value);
    }

    if (value instanceof Float) {
      return DoubleNode.valueOf((Float) value);
    }

    if (value instanceof Double) {
      return DoubleNode.valueOf((Double) value);
    }

    if (value instanceof Integer) {
      return IntNode.valueOf((Integer) value);
    }

    if (value instanceof Long) {
      return LongNode.valueOf((Long) value);
    }

    if (value instanceof Boolean) {
      return BooleanNode.valueOf((Boolean) value);
    }

    return jsonObject();
  }
}
