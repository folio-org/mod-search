package org.folio.search.utils;

import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.model.types.FieldType.PLAIN;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.model.types.FieldType;
import org.springframework.test.web.servlet.ResultActions;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestUtils {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
    .setSerializationInclusion(Include.NON_NULL)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

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
  public static <T> T parseResponse(ResultActions result, Class<T> type) {
    return OBJECT_MAPPER.readValue(result.andReturn().getResponse()
      .getContentAsString(), type);
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

  @SuppressWarnings("unchecked")
  public static <K, V> Map<K, V> mapOf(K k1, V v1, Object... pairs) {
    Map<K, V> map = new LinkedHashMap<>();
    map.put(k1, v1);
    for (int i = 0; i < pairs.length; i += 2) {
      Object key = pairs[i];
      Object value = pairs[i + 1];
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

  public static ResourceDescription resourceDescription(
    Map<String, FieldDescription> fields, List<String> languageSourcePaths) {
    var resourceDescription = new ResourceDescription();
    resourceDescription.setIndex(INDEX_NAME);
    resourceDescription.setName(RESOURCE_NAME);
    resourceDescription.setFields(fields);
    resourceDescription.setLanguageSourcePaths(languageSourcePaths);
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
    return fieldDescription;
  }

  public static PlainFieldDescription keywordField() {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setType(PLAIN);
    fieldDescription.setIndex("keyword");
    return fieldDescription;
  }

  public static PlainFieldDescription multilangField() {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setType(PLAIN);
    fieldDescription.setIndex(MULTILANG_FIELD_TYPE);
    return fieldDescription;
  }

  public static ObjectFieldDescription objectField(Map<String, FieldDescription> props) {
    var objectFieldDescription = new ObjectFieldDescription();
    objectFieldDescription.setType(FieldType.OBJECT);
    objectFieldDescription.setProperties(props);
    return objectFieldDescription;
  }

  public static SearchFieldDescriptor searchField(String processor) {
    var fieldDescription = new SearchFieldDescriptor();
    fieldDescription.setType(PLAIN);
    fieldDescription.setIndex("keyword");
    fieldDescription.setProcessor(processor);
    return fieldDescription;
  }

  public static ResourceEventBody eventBody(String resourceName, Object newData) {
    var resourceBody = new ResourceEventBody();
    resourceBody.setType(ResourceEventBody.TypeEnum.CREATE);
    resourceBody.setResourceName(resourceName);
    resourceBody.setTenant(TENANT_ID);
    resourceBody.setNew(newData);
    return resourceBody;
  }
}
