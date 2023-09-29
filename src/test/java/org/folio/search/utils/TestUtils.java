package org.folio.search.utils;

import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.model.metadata.PlainFieldDescription.STANDARD_FIELD_TYPE;
import static org.folio.search.model.types.FieldType.OBJECT;
import static org.folio.search.model.types.FieldType.PLAIN;
import static org.folio.search.model.types.FieldType.SEARCH;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.CallNumberUtils.normalizeEffectiveShelvingOrder;
import static org.folio.search.utils.JsonUtils.jsonArray;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.TestConstants.EMPTY_OBJECT;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.DeprecationHandler.IGNORE_DEPRECATIONS;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.folio.search.cql.EffectiveShelvingOrderTermProcessor;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthorityBrowseItem;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.CallNumberBrowseResult;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetItem;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Identifiers;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceContributorBrowseItem;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.domain.dto.SubjectBrowseItem;
import org.folio.search.domain.dto.SubjectBrowseResult;
import org.folio.search.domain.dto.Tags;
import org.folio.search.model.SearchResult;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.model.service.CqlFacetRequest;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.model.types.CallNumberType;
import org.folio.search.model.types.IndexingDataFormat;
import org.folio.search.model.types.SearchType;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.core.ParseField;
import org.opensearch.core.xcontent.ContextParser;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.bucket.filter.ParsedFilter;
import org.opensearch.search.aggregations.bucket.range.ParsedRange;
import org.opensearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.ResultActions;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestUtils {

  public static final String KEYWORD_FIELD_TYPE = "keyword";
  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
    .setSerializationInclusion(Include.NON_NULL)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
  public static final SmileMapper SMILE_MAPPER = new SmileMapper();

  public static final NamedXContentRegistry NAMED_XCONTENT_REGISTRY =
    new NamedXContentRegistry(TestUtils.elasticsearchClientNamedContentRegistryEntries());
  private static final EffectiveShelvingOrderTermProcessor SHELVING_ORDER_TERM_PROCESSOR =
    new EffectiveShelvingOrderTermProcessor();

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

  public static CqlSearchRequest<Instance> searchServiceRequest(String query) {
    return searchServiceRequest(Instance.class, query);
  }

  public static <T> CqlSearchRequest<T> searchServiceRequest(Class<T> resourceClass, String query) {
    return searchServiceRequest(resourceClass, query, false);
  }

  public static <T> CqlSearchRequest<T> searchServiceRequest(Class<T> resourceClass, String tenantId, String query) {
    return searchServiceRequest(resourceClass, tenantId, query, false);
  }

  public static <T> CqlSearchRequest<T> searchServiceRequest(Class<T> resourceClass, String query, boolean expandAll) {
    return searchServiceRequest(resourceClass, TENANT_ID, query, expandAll);
  }

  public static <T> CqlSearchRequest<T> searchServiceRequest(Class<T> resourceClass, String tenantId, String query,
                                                             boolean expandAll) {
    return CqlSearchRequest.of(resourceClass, tenantId, query, 100, 0, expandAll);
  }

  public static CqlFacetRequest defaultFacetServiceRequest(String resource, String query, String... facets) {
    return facetServiceRequest(TENANT_ID, resource, query, facets);
  }

  public static CqlFacetRequest facetServiceRequest(String tenantId, String resource, String query, String... facets) {
    return CqlFacetRequest.of(resource, tenantId, query, asList(facets));
  }

  public static CallNumberBrowseResult cnBrowseResult(int total, List<CallNumberBrowseItem> items) {
    return new CallNumberBrowseResult().totalRecords(total).items(items);
  }

  public static CallNumberBrowseItem cnBrowseItem(Instance instance, String callNumber) {
    var callNumberType = Optional.ofNullable(instance.getItems().get(0))
      .flatMap(item -> Optional.ofNullable(item.getEffectiveCallNumberComponents())
        .map(ItemEffectiveCallNumberComponents::getTypeId));
    var shelfKey = callNumberType.isEmpty()
      ? getShelfKeyFromCallNumber(callNumber)
      : getShelfKeyFromCallNumber(callNumber, callNumberType.get());
    return cnBrowseItem(instance, shelfKey, callNumber);
  }

  public static CallNumberBrowseItem cnBrowseItem(Instance instance, String shelfKey, String callNumber) {
    return new CallNumberBrowseItem().fullCallNumber(callNumber).shelfKey(shelfKey).instance(instance).totalRecords(1);
  }

  public static CallNumberBrowseItem cnBrowseItem(Instance instance, String callNumber, boolean isAnchor) {
    var shelfKey = getShelfKeyFromCallNumber(callNumber);
    return new CallNumberBrowseItem().fullCallNumber(callNumber).shelfKey(shelfKey)
      .instance(instance).totalRecords(1).isAnchor(isAnchor);
  }

  public static CallNumberBrowseItem cnBrowseItem(int totalRecords, String callNumber) {
    var shelfKey = getShelfKeyFromCallNumber(callNumber);
    return new CallNumberBrowseItem().totalRecords(totalRecords).shelfKey(shelfKey).fullCallNumber(callNumber);
  }

  public static CallNumberBrowseItem cnBrowseItem(int totalRecords, String callNumber, boolean isAnchor) {
    var shelfKey = getShelfKeyFromCallNumber(callNumber);
    return new CallNumberBrowseItem().totalRecords(totalRecords)
      .shelfKey(shelfKey).fullCallNumber(callNumber).isAnchor(isAnchor);
  }

  public static String getShelfKeyFromCallNumber(String callNumber) {
    var terms = SHELVING_ORDER_TERM_PROCESSOR.getSearchTerms(callNumber);
    return terms.get(terms.size() - 1);
  }

  public static String getShelfKeyFromCallNumber(String callNumber, String typeId) {
    var callNumberType = CallNumberType.fromId(typeId);
    if (callNumberType.isEmpty()) {
      return normalizeEffectiveShelvingOrder(callNumber);
    }

    return getShelfKeyFromCallNumber(callNumber);
  }

  public static SubjectBrowseResult subjectBrowseResult(int total, List<SubjectBrowseItem> items) {
    return new SubjectBrowseResult().totalRecords(total).items(items);
  }

  public static SubjectBrowseItem subjectBrowseItem(Integer totalRecords, String subject) {
    return new SubjectBrowseItem().value(subject).totalRecords(totalRecords);
  }

  public static SubjectBrowseItem subjectBrowseItem(Integer totalRecords, String subject, String authorityId) {
    return new SubjectBrowseItem().value(subject).authorityId(authorityId).totalRecords(totalRecords);
  }

  public static SubjectBrowseItem subjectBrowseItem(Integer totalRecords, String subject, boolean isAnchor) {
    return new SubjectBrowseItem().value(subject).totalRecords(totalRecords).isAnchor(isAnchor);
  }

  public static SubjectBrowseItem subjectBrowseItem(Integer totalRecords, String subject, String authorityId,
                                                    boolean isAnchor) {
    return new SubjectBrowseItem().value(subject)
      .authorityId(authorityId)
      .totalRecords(totalRecords)
      .isAnchor(isAnchor);
  }

  public static SubjectBrowseItem subjectBrowseItem(String subject) {
    return new SubjectBrowseItem().value(subject);
  }

  public static InstanceContributorBrowseItem contributorBrowseItem(Integer totalRecords, String name,
                                                                    String nameTypeId, String authorityId,
                                                                    String... typeIds) {
    return contributorBrowseItem(totalRecords, false, name, nameTypeId, authorityId, typeIds);
  }

  public static InstanceContributorBrowseItem contributorBrowseItem(Integer totalRecords, boolean isAnchor,
                                                                    String name) {
    return new InstanceContributorBrowseItem().name(name).totalRecords(totalRecords).isAnchor(isAnchor);
  }

  public static InstanceContributorBrowseItem contributorBrowseItem(Integer totalRecords, boolean isAnchor,
                                                                    String name, String nameTypeId,
                                                                    String authorityId, String... typeIds) {
    return new InstanceContributorBrowseItem()
      .name(name)
      .contributorNameTypeId(nameTypeId)
      .authorityId(authorityId)
      .contributorTypeId(Arrays.asList(typeIds))
      .totalRecords(totalRecords)
      .isAnchor(isAnchor);
  }

  public static AuthorityBrowseItem authorityBrowseItem(String heading, Authority authority) {
    return new AuthorityBrowseItem().headingRef(heading).authority(authority);
  }

  public static AuthorityBrowseItem authorityBrowseItem(String heading, Authority authority, boolean isAnchor) {
    return new AuthorityBrowseItem().headingRef(heading).isAnchor(isAnchor).authority(authority);
  }

  public static String randomId() {
    return UUID.randomUUID().toString();
  }

  @SneakyThrows
  public static SearchDocumentBody searchDocumentBody() {
    return SearchDocumentBody.of(new BytesArray(SMILE_MAPPER.writeValueAsBytes(EMPTY_OBJECT)),
      IndexingDataFormat.SMILE, resourceEvent(), INDEX);
  }

  @SneakyThrows
  public static SearchDocumentBody searchDocumentBody(String rawJson) {
    return SearchDocumentBody.of(new BytesArray(SMILE_MAPPER.writeValueAsBytes(rawJson)),
      IndexingDataFormat.SMILE, resourceEvent(), INDEX);
  }

  public static SearchDocumentBody searchDocumentBodyToDelete() {
    return SearchDocumentBody.of(null, null, resourceEvent(), DELETE);
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
    return resourceDescription(name, emptyMap());
  }

  public static ResourceDescription resourceDescription(Map<String, FieldDescription> fields) {
    return resourceDescription(RESOURCE_NAME, fields);
  }

  public static ResourceDescription resourceDescription(
    Map<String, FieldDescription> fields, List<String> languageSourcePaths) {
    var resourceDescription = resourceDescription(RESOURCE_NAME, fields);
    resourceDescription.setLanguageSourcePaths(languageSourcePaths);
    return resourceDescription;
  }

  public static ResourceDescription resourceDescription(String name, Map<String, FieldDescription> fields) {
    var resourceDescription = new ResourceDescription();
    resourceDescription.setName(name);
    resourceDescription.setFields(fields);
    resourceDescription.setReindexSupported(true);
    return resourceDescription;
  }

  public static ResourceDescription secondaryResourceDescription(String name, String parent) {
    var resourceDescription = resourceDescription(name, emptyMap());
    resourceDescription.setParent(parent);
    resourceDescription.setReindexSupported(false);
    return resourceDescription;
  }

  public static PlainFieldDescription plainField(String index) {
    return plainField(index, emptyList());
  }

  public static PlainFieldDescription plainField(String index, ObjectNode mappings) {
    var fieldDescription = plainField(index, emptyList());
    fieldDescription.setMappings(mappings);
    return fieldDescription;
  }

  public static PlainFieldDescription plainField(String index, List<SearchType> searchTypes) {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setType(PLAIN);
    fieldDescription.setIndex(index);
    fieldDescription.setSearchTypes(searchTypes);
    return fieldDescription;
  }

  public static PlainFieldDescription keywordField(SearchType... searchTypes) {
    return plainField(KEYWORD_FIELD_TYPE, asList(searchTypes));
  }

  public static PlainFieldDescription standardField(SearchType... searchTypes) {
    return plainField(STANDARD_FIELD_TYPE, asList(searchTypes));
  }

  public static PlainFieldDescription standardField(boolean isPlainFieldIndexed, SearchType... searchTypes) {
    var desc = plainField(STANDARD_FIELD_TYPE, asList(searchTypes));
    desc.setIndexPlainValue(isPlainFieldIndexed);
    return desc;
  }

  public static PlainFieldDescription keywordFieldWithDefaultValue(Object defaultValue) {
    var keywordField = keywordField();
    keywordField.setDefaultValue(defaultValue);
    return keywordField;
  }

  public static PlainFieldDescription filterField() {
    return plainField(KEYWORD_FIELD_TYPE, List.of(SearchType.FILTER));
  }

  public static PlainFieldDescription multilangField() {
    return plainField(MULTILANG_FIELD_TYPE, emptyList());
  }

  public static PlainFieldDescription multilangField(String... searchAliases) {
    var field = plainField(MULTILANG_FIELD_TYPE, emptyList());
    field.setSearchAliases(List.of(searchAliases));
    return field;
  }

  public static PlainFieldDescription standardFulltextField() {
    return plainField(STANDARD_FIELD_TYPE, emptyList());
  }

  public static ObjectFieldDescription objectField(Map<String, FieldDescription> props) {
    var objectFieldDescription = new ObjectFieldDescription();
    objectFieldDescription.setType(OBJECT);
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

  public static ResourceEvent resourceEvent() {
    return resourceEvent(RESOURCE_ID, RESOURCE_NAME, CREATE, null, null);
  }

  public static ResourceEvent resourceEvent(String resource, Object newData) {
    return resourceEvent(RESOURCE_ID, resource, CREATE, newData, null);
  }

  public static ResourceEvent resourceEvent(String id, String resource, Object newData) {
    return resourceEvent(id, resource, CREATE, newData, null);
  }

  public static ResourceEvent resourceEvent(String id, String resource, ResourceEventType type) {
    return resourceEvent(id, resource, type, null, null);
  }

  public static ResourceEvent resourceEvent(String id, String resource, ResourceEventType type, Object n, Object o) {
    return new ResourceEvent().id(id).type(type).resourceName(resource).tenant(TENANT_ID)._new(n).old(o);
  }

  public static ResourceEvent kafkaResourceEvent(ResourceEventType type, Object newData, Object oldData) {
    return kafkaResourceEvent(TENANT_ID, type, newData, oldData);
  }

  public static ResourceEvent kafkaResourceEvent(String tenant, ResourceEventType type, Object n, Object o) {
    return new ResourceEvent().type(type).tenant(tenant)._new(n).old(o);
  }

  @SafeVarargs
  public static <T> SearchResult<T> searchResult(T... records) {
    return new SearchResult<T>().totalRecords(records.length).records(List.of(records));
  }

  @SafeVarargs
  public static <T> SearchResult<T> searchResult(int totalRecords, T... records) {
    return new SearchResult<T>().totalRecords(totalRecords).records(List.of(records));
  }

  public static <T> SearchResult<T> searchResult(List<T> records) {
    return new SearchResult<T>().totalRecords(records.size()).records(records);
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

  public static Identifiers identifier(String id, String value) {
    return new Identifiers().identifierTypeId(id).value(value);
  }

  public static Instance instanceWithIdentifiers(Identifiers... identifiers) {
    return new Instance().id(RESOURCE_ID).identifiers(identifiers != null ? asList(identifiers) : null);
  }

  public static Authority authorityWithIdentifiers(Identifiers... identifiers) {
    return new Authority().identifiers(identifiers != null ? asList(identifiers) : null);
  }

  public static Map<String, Object> toMap(Object value) {
    return OBJECT_MAPPER.convertValue(value, new TypeReference<>() { });
  }

  public static <T> void doIfNotNull(T value, Consumer<T> valueConsumer) {
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

  public static void cleanUpCaches(CacheManager cacheManager) {
    cacheManager.getCacheNames().forEach(name -> requireNonNull(cacheManager.getCache(name)).clear());
  }

  @SneakyThrows
  public static Aggregations aggregationsFromJson(JsonNode aggregationNode) {
    var jsonString = searchResponseWithAggregation(aggregationNode).toString();
    var parser = jsonXContent.createParser(NAMED_XCONTENT_REGISTRY, IGNORE_DEPRECATIONS, jsonString);
    return SearchResponse.fromXContent(parser).getAggregations();
  }

  public static List<NamedXContentRegistry.Entry> elasticsearchClientNamedContentRegistryEntries() {
    Map<String, ContextParser<Object, ? extends Aggregation>> map = new HashMap<>();
    map.put("sterms", (p, c) -> ParsedStringTerms.fromXContent(p, (String) c));
    map.put("range", (p, c) -> ParsedRange.fromXContent(p, (String) c));
    map.put("filter", (p, c) -> ParsedFilter.fromXContent(p, (String) c));
    map.put("string_stats", (p, c) -> ParsedStringStats.PARSER.parse(p, (String) c));
    return map.entrySet().stream()
      .map(v -> new NamedXContentRegistry.Entry(Aggregation.class, new ParseField(v.getKey()), v.getValue()))
      .toList();
  }

  @SuppressWarnings("unchecked")
  public static <T, P extends T> P spyLambda(Class<T> lambdaType, P lambda) {
    return (P) mock(lambdaType, delegatesTo(lambda));
  }

  private static JsonNode searchResponseWithAggregation(JsonNode aggregationValue) {
    return jsonObject(
      "took", 0,
      "timed_out", false,
      "_shards", jsonObject("total", 1, "successful", 1, "skipped", 0, "failed", 0),
      "hits", jsonObject("total", jsonObject("value", 0, "relation", "eq"), "max_score", null, "hits", jsonArray()),
      "aggregations", aggregationValue);
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(staticName = "of")
  public static class TestResource {

    private String id;

    public TestResource id(String id) {
      this.id = id;
      return this;
    }
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(staticName = "of")
  public static class TestClass {
    private String field;
  }

  public static class NonSerializableByJacksonClass {
    private final NonSerializableByJacksonClass self = this;

    @SuppressWarnings("unused")
    public NonSerializableByJacksonClass getSelf() {
      return self;
    }
  }
}
