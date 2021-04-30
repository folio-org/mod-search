package org.folio.search.utils;

import static java.util.Arrays.asList;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.model.types.FieldType.PLAIN;
import static org.folio.search.model.types.FieldType.SEARCH;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetItem;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.domain.dto.SearchResult;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.model.service.CqlFacetServiceRequest;
import org.folio.search.model.service.CqlSearchServiceRequest;
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

  @SneakyThrows
  public static <T> T parseResponse(ResultActions result, TypeReference<T> type) {
    return OBJECT_MAPPER.readValue(result.andReturn().getResponse()
      .getContentAsString(), type);
  }

  public static CqlSearchServiceRequest searchServiceRequest(String query) {
    return searchServiceRequest(INSTANCE_RESOURCE, query);
  }

  public static CqlSearchServiceRequest searchServiceRequest(String resource, String query) {
    return searchServiceRequest(resource, query, false);
  }

  public static CqlSearchServiceRequest searchServiceRequest(String resource, String query, boolean expandAll) {
    var rq = new CqlSearchServiceRequest();
    rq.query(query).limit(100).offset(0);
    rq.setResource(resource);
    rq.setTenantId(TENANT_ID);
    rq.setExpandAll(expandAll);
    return rq;
  }

  public static CqlFacetServiceRequest facetServiceRequest(String query, String...facets) {
    var request = new CqlFacetServiceRequest();
    request.setQuery(query);
    request.setResource(INSTANCE_RESOURCE);
    request.setTenantId(TENANT_ID);
    request.setFacet(asList(facets));
    return request;
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

  public static PlainFieldDescription plainField(String index, ObjectNode mappings) {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setType(PLAIN);
    fieldDescription.setIndex(index);
    fieldDescription.setMappings(mappings);
    return fieldDescription;
  }

  public static PlainFieldDescription keywordField() {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setType(PLAIN);
    fieldDescription.setIndex("keyword");
    return fieldDescription;
  }

  public static PlainFieldDescription keywordField(SearchType ... searchTypes) {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setType(PLAIN);
    fieldDescription.setIndex("keyword");
    fieldDescription.setSearchTypes(asList(searchTypes));
    return fieldDescription;
  }

  public static PlainFieldDescription keywordFieldWithDefaultValue(Object defaultValue) {
    var keywordField = keywordField();
    keywordField.setDefaultValue(defaultValue);
    return keywordField;
  }

  public static PlainFieldDescription filterField() {
    var fieldDescription = keywordField();
    fieldDescription.setSearchTypes(List.of(SearchType.FILTER));
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

  public static SearchResult searchResult(Instance... instances) {
    var searchResult = new SearchResult();
    searchResult.setInstances(List.of(instances));
    searchResult.setTotalRecords(instances.length);
    return searchResult;
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
}
