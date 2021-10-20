package org.folio.search.utils;

import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toCollection;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.model.metadata.PlainFieldDescription.STANDARD_FIELD_TYPE;
import static org.folio.search.model.types.FieldType.PLAIN;
import static org.folio.search.model.types.FieldType.SEARCH;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.utils.TestConstants.EMPTY_OBJECT;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetItem;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceIdentifiers;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.domain.dto.Tags;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.SearchResult;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.model.service.CqlFacetRequest;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.model.types.FieldType;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.model.types.SearchType;
import org.springframework.test.web.servlet.ResultActions;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestUtils {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
    .setSerializationInclusion(Include.NON_NULL)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

  public static final String KEYWORD_FIELD_TYPE = "keyword";

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

  @SneakyThrows
  public static <T> T parseResponse(ResultActions result, Class<T> type) {
    return OBJECT_MAPPER.readValue(result.andReturn().getResponse().getContentAsString(), type);
  }

  @SneakyThrows
  public static <T> T parseResponse(ResultActions result, TypeReference<T> type) {
    return OBJECT_MAPPER.readValue(result.andReturn().getResponse()
      .getContentAsString(), type);
  }

  public static CqlSearchRequest<TestResource> searchServiceRequest(String query) {
    return searchServiceRequest(TestResource.class, query);
  }

  public static <T> CqlSearchRequest<T> searchServiceRequest(Class<T> resourceClass, String query) {
    return searchServiceRequest(resourceClass, query, false);
  }

  public static <T> CqlSearchRequest<T> searchServiceRequest(Class<T> resourceClass, String query, boolean expandAll) {
    return CqlSearchRequest.of(resourceClass, TENANT_ID, query, 100, 0, expandAll);
  }

  public static CqlFacetRequest facetServiceRequest(String resource, String query, String... facets) {
    return CqlFacetRequest.of(resource, TENANT_ID, query, asList(facets));
  }

  public static String randomId() {
    return UUID.randomUUID().toString();
  }

  public static SearchDocumentBody searchDocumentBody() {
    return SearchDocumentBody.builder()
      .id(RESOURCE_ID)
      .index(INDEX_NAME)
      .routing(TENANT_ID)
      .rawJson(EMPTY_OBJECT)
      .action(IndexActionType.INDEX)
      .build();
  }

  public static SearchDocumentBody searchDocumentBodyForDelete() {
    return SearchDocumentBody.builder()
      .id(RESOURCE_ID)
      .index(INDEX_NAME)
      .routing(TENANT_ID)
      .action(DELETE)
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
  public static <T> Set<T> setOf(T... values) {
    return Arrays.stream(values).collect(toCollection(LinkedHashSet::new));
  }

  @SafeVarargs
  public static <T> T[] array(T... values) {
    return values;
  }

  public static ResourceDescription resourceDescription(String name) {
    var resourceDescription = new ResourceDescription();
    resourceDescription.setName(name);
    return resourceDescription;
  }

  public static ResourceDescription resourceDescription(Map<String, FieldDescription> fields) {
    var resourceDescription = new ResourceDescription();
    resourceDescription.setName(RESOURCE_NAME);
    resourceDescription.setFields(fields);
    return resourceDescription;
  }

  public static ResourceDescription resourceDescription(
    Map<String, FieldDescription> fields, List<String> languageSourcePaths) {
    var resourceDescription = new ResourceDescription();
    resourceDescription.setName(RESOURCE_NAME);
    resourceDescription.setFields(fields);
    resourceDescription.setLanguageSourcePaths(languageSourcePaths);
    return resourceDescription;
  }

  public static PlainFieldDescription plainField(String index) {
    return plainField(PLAIN, index, emptyList());
  }

  public static PlainFieldDescription plainField(String index, ObjectNode mappings) {
    var fieldDescription = plainField(PLAIN, index, emptyList());
    fieldDescription.setMappings(mappings);
    return fieldDescription;
  }

  public static PlainFieldDescription plainField(FieldType type, String index, List<SearchType> searchTypes) {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setType(type);
    fieldDescription.setIndex(index);
    fieldDescription.setSearchTypes(searchTypes);
    return fieldDescription;
  }

  public static PlainFieldDescription keywordField(SearchType... searchTypes) {
    return plainField(PLAIN, KEYWORD_FIELD_TYPE, asList(searchTypes));
  }

  public static PlainFieldDescription standardField(SearchType... searchTypes) {
    return plainField(PLAIN, STANDARD_FIELD_TYPE, asList(searchTypes));
  }

  public static PlainFieldDescription standardField(boolean isPlainFieldIndexed, SearchType... searchTypes) {
    var desc = plainField(PLAIN, STANDARD_FIELD_TYPE, asList(searchTypes));
    desc.setIndexPlainValue(isPlainFieldIndexed);
    return desc;
  }

  public static PlainFieldDescription keywordFieldWithDefaultValue(Object defaultValue) {
    var keywordField = keywordField();
    keywordField.setDefaultValue(defaultValue);
    return keywordField;
  }

  public static PlainFieldDescription filterField() {
    return plainField(PLAIN, KEYWORD_FIELD_TYPE, List.of(SearchType.FILTER));
  }

  public static PlainFieldDescription multilangField() {
    return plainField(PLAIN, MULTILANG_FIELD_TYPE, emptyList());
  }

  public static PlainFieldDescription multilangField(String... inventorySearchType) {
    var field = plainField(PLAIN, MULTILANG_FIELD_TYPE, emptyList());
    field.setInventorySearchTypes(List.of(inventorySearchType));
    return field;
  }

  public static PlainFieldDescription standardFulltextField() {
    return plainField(PLAIN, STANDARD_FIELD_TYPE, emptyList());
  }

  public static ObjectFieldDescription objectField(Map<String, FieldDescription> props) {
    var objectFieldDescription = new ObjectFieldDescription();
    objectFieldDescription.setType(FieldType.OBJECT);
    objectFieldDescription.setProperties(props);
    return objectFieldDescription;
  }

  public static SearchFieldDescriptor searchField(String processor) {
    var fieldDescription = new SearchFieldDescriptor();
    fieldDescription.setType(SEARCH);
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

  @SafeVarargs
  public static <T> SearchResult<T> searchResult(T... records) {
    return SearchResult.of(records.length, List.of(records));
  }

  public static Facet facet(List<FacetItem> items) {
    return new Facet().values(items).totalRecords(items.size());
  }

  public static Facet facet(FacetItem... items) {
    return new Facet().values(asList(items)).totalRecords(items.length);
  }

  public static FacetItem facetItem(String id, int totalRecords) {
    return new FacetItem().id(id).totalRecords(BigDecimal.valueOf(totalRecords));
  }

  public static FacetResult facetResult(Map<String, Facet> facets) {
    return new FacetResult().facets(facets).totalRecords(facets.size());
  }

  public static LanguageConfig languageConfig(String code) {
    return new LanguageConfig().code(code);
  }

  public static LanguageConfig languageConfig(String code, String analyzer) {
    return new LanguageConfig().code(code).languageAnalyzer(analyzer);
  }

  public static LanguageConfigs languageConfigs(List<LanguageConfig> configs) {
    return new LanguageConfigs().languageConfigs(configs).totalRecords(configs.size());
  }

  public static Tags tags(String... tagValues) {
    return new Tags().tagList(tagValues != null ? asList(tagValues) : null);
  }

  public static InstanceIdentifiers identifier(String id, String value) {
    return new InstanceIdentifiers().identifierTypeId(id).value(value);
  }

  public static Instance instanceWithIdentifiers(InstanceIdentifiers... identifiers) {
    return new Instance().identifiers(identifiers != null ? asList(identifiers) : null);
  }

  public static Map<String, Object> toMap(Instance instance) {
    return OBJECT_MAPPER.convertValue(instance, new TypeReference<>() {});
  }

  public static void doIfNotNull(Object value, Consumer<Object> valueConsumer) {
    if (value != null) {
      valueConsumer.accept(value);
    }
  }

  public static void setEnvProperty(String value) {
    setProperty("env", value);
  }

  public static void removeEnvProperty() {
    clearProperty("env");
  }

  @Data
  public static class TestResource {

    private String id;

    public TestResource id(String id) {
      this.id = id;
      return this;
    }
  }
}
