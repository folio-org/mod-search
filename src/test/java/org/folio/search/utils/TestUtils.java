package org.folio.search.utils;

import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.model.types.FieldType.PLAIN;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.types.FieldType;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestUtils {


  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @SneakyThrows
  public static String asJsonString(Object value) {
    return OBJECT_MAPPER.writeValueAsString(value);
  }

  public static String randomId() {
    return UUID.randomUUID().toString();
  }

  public static SearchDocumentBody searchDocumentBody() {
    return SearchDocumentBody.builder()
      .id(UUID.randomUUID().toString())
      .index(INDEX_NAME)
      .routing(TestConstants.TENANT_ID)
      .rawJson(TestConstants.EMPTY_OBJECT)
      .build();
  }

  public static <K, V> Map<K, V> mapOf(K k1, V v1, Object... pairs) {
    Map<K, V> map = new LinkedHashMap<>();
    map.put(k1, v1);
    for (int i = 0; i < pairs.length; i += 2) {
      Object key = pairs[i];
      Object value = pairs[i + 1];
      //noinspection unchecked
      map.put((K) key, (V) value);
    }
    return map;
  }

  @SafeVarargs
  public static <T> T[] array(T... values) {
    return values;
  }

  public static ResourceDescription resourceDescription(Map<String, FieldDescription> fields) {
    var resourceDescription = new ResourceDescription();
    resourceDescription.setIndex(INDEX_NAME);
    resourceDescription.setName(RESOURCE_NAME);
    resourceDescription.setFields(fields);
    return resourceDescription;
  }

  public static PlainFieldDescription plainField(String index) {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setType(PLAIN);
    fieldDescription.setIndex(index);
    return fieldDescription;
  }

  public static PlainFieldDescription plainField(String index, String path) {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setType(PLAIN);
    fieldDescription.setIndex(index);
    fieldDescription.setSourcePath(path);
    return fieldDescription;
  }

  public static PlainFieldDescription plainField(String index, ObjectNode mappings) {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setType(PLAIN);
    fieldDescription.setIndex(index);
    fieldDescription.setMappings(mappings);
    return fieldDescription;
  }

  public static PlainFieldDescription languageField(String index, String path) {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setType(PLAIN);
    fieldDescription.setIndex(index);
    fieldDescription.setLanguageSource(true);
    fieldDescription.setSourcePath(path);
    return fieldDescription;
  }

  public static PlainFieldDescription multilangField(String path) {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setType(PLAIN);
    fieldDescription.setIndex(MULTILANG_FIELD_TYPE);
    fieldDescription.setSourcePath(path);
    return fieldDescription;
  }

  public static ObjectFieldDescription objectField(Map<String, FieldDescription> props) {
    var objectFieldDescription = new ObjectFieldDescription();
    objectFieldDescription.setType(FieldType.OBJECT);
    objectFieldDescription.setProperties(props);
    return objectFieldDescription;
  }
}
