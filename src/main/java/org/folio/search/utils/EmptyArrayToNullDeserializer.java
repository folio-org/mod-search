package org.folio.search.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.IOException;
import java.util.List;

public class EmptyArrayToNullDeserializer extends JsonDeserializer<List<String>> {

  @Override
  public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    // Get the default deserializer for List<String>
    ObjectMapper mapper = (ObjectMapper) p.getCodec();
    JavaType type = ctxt.constructType(List.class);
    CollectionType stringListType = mapper.getTypeFactory().constructCollectionType(List.class, String.class);

    List<String> list = mapper.readValue(p, stringListType);

    if (list != null && list.isEmpty()) {
      return null;
    }
    return list;
  }
}
