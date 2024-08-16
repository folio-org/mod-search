package org.folio.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.TWO_SECONDS;
import static org.folio.search.configuration.SearchCacheNames.REFERENCE_DATA_CACHE;
import static org.folio.search.domain.dto.TenantConfiguredFeature.SEARCH_ALL_FIELDS;
import static org.folio.search.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.search.support.base.ApiEndpoints.featureConfigPath;
import static org.folio.search.utils.SearchConverterUtils.getMapValueByPath;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestConstants.inventoryClassificationTopic;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.mockClassificationTypes;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.opensearch.index.query.QueryBuilders.matchQuery;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseConfigCollection;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.support.base.ApiEndpoints;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.TestUtils;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ConfigIT extends BaseIntegrationTest {

  @Autowired
  private RestHighLevelClient elasticsearchClient;

  @BeforeAll
  static void prepare() {
    setUpTenant(Instance.class, getSemanticWebAsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @BeforeEach
  void removeConfigs() {
    parseResponse(doGet(ApiEndpoints.languageConfigPath()), LanguageConfigs.class)
      .getLanguageConfigs()
      .forEach(config -> doDelete(ApiEndpoints.languageConfigPath() + "/{code}", config.getCode()));
  }

  @Test
  void canCreateLanguageConfig() throws Exception {
    final var languageCode = "eng";

    doPost(ApiEndpoints.languageConfigPath(), new LanguageConfig().code(languageCode));

    doGet(ApiEndpoints.languageConfigPath())
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("languageConfigs[0].code", is(languageCode)));
  }

  @Test
  void canCreateLanguageConfigWithCustomAnalyzer() throws Exception {
    final var languageCode = "kor";
    final var analyzer = "nori";

    doPost(ApiEndpoints.languageConfigPath(), TestUtils.languageConfig(languageCode, analyzer));

    doGet(ApiEndpoints.languageConfigPath())
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("languageConfigs[0].code", is(languageCode)))
      .andExpect(jsonPath("languageConfigs[0].languageAnalyzer", is(analyzer)));

    var newAnalyzer = "seunjeon_analyzer";
    doPut(ApiEndpoints.languageConfigPath() + "/kor", TestUtils.languageConfig(languageCode, newAnalyzer))
      .andExpect(jsonPath("languageAnalyzer", is(newAnalyzer)));

    doGet(ApiEndpoints.languageConfigPath())
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("languageConfigs[0].code", is(languageCode)))
      .andExpect(jsonPath("languageConfigs[0].languageAnalyzer", is(newAnalyzer)));
  }

  @Test
  void cannotUpdateNonExistingLanguageConfig() throws Exception {
    final var languageCode = "kor";
    final var analyzer = "nori";

    doPost(ApiEndpoints.languageConfigPath(), TestUtils.languageConfig(languageCode, analyzer));

    doGet(ApiEndpoints.languageConfigPath())
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("languageConfigs[0].code", is(languageCode)))
      .andExpect(jsonPath("languageConfigs[0].languageAnalyzer", is(analyzer)));
  }

  @Test
  void cannotAddLanguageIfNoAnalyzer() throws Exception {
    attemptPost(ApiEndpoints.languageConfigPath(), new LanguageConfig().code("ukr"))
      .andExpect(status().is(422))
      .andExpect(jsonPath("total_records", is(1)))
      .andExpect(jsonPath("errors[0].parameters[0].key", is("code")))
      .andExpect(jsonPath("errors[0].parameters[0].value", is("ukr")))
      .andExpect(jsonPath("errors[0].message",
        is("Language has no analyzer available")));
  }

  @Test
  void canRemoveLanguageConfig() throws Exception {
    final var language = new LanguageConfig().code("fre");

    doPost(ApiEndpoints.languageConfigPath(), language);

    doDelete(ApiEndpoints.languageConfigPath() + "/fre")
      .andExpect(status().isNoContent());
  }

  @Test
  @Disabled("Check how to validate configured languages if values are excluded from source (dev setting for _source)")
  @SuppressWarnings("unchecked")
  void shouldUseConfiguredLanguagesDuringMapping() {
    final List<String> languageCodes = List.of("eng", "rus");
    for (String languageCode : languageCodes) {
      doPost(ApiEndpoints.languageConfigPath(), new LanguageConfig().code(languageCode));
    }

    var newInstance = new Instance()
      .id(randomId())
      .languages(List.of("eng", "rus", "fre"))
      .title("This is title");

    inventoryApi.createInstance(TENANT_ID, newInstance);

    final var indexedInstance = getIndexedInstanceById(newInstance.getId());

    assertThat((Map<String, Object>) getMapValueByPath("title", indexedInstance)).hasSize(3);
    assertThat(getMapValueByPath("title.eng", indexedInstance)).isEqualTo(newInstance.getTitle());
    assertThat(getMapValueByPath("title.rus", indexedInstance)).isEqualTo(newInstance.getTitle());
    assertThat(getMapValueByPath("title.src", indexedInstance)).isEqualTo(newInstance.getTitle());
    assertThat(getMapValueByPath("title.fre", indexedInstance)).isNull();
  }

  @Test
  void featureConfigurationWorkflow_positive() throws Exception {
    var feature = new FeatureConfig().feature(SEARCH_ALL_FIELDS).enabled(true);
    doPost(ApiEndpoints.featureConfigPath(), feature)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.feature", is(SEARCH_ALL_FIELDS.getValue())))
      .andExpect(jsonPath("$.enabled", is(true)));

    var featureToUpdate = new FeatureConfig().feature(SEARCH_ALL_FIELDS).enabled(false);
    doPut(ApiEndpoints.featureConfigPath(SEARCH_ALL_FIELDS), featureToUpdate)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.feature", is(SEARCH_ALL_FIELDS.getValue())))
      .andExpect(jsonPath("$.enabled", is(false)));

    doDelete(featureConfigPath(SEARCH_ALL_FIELDS))
      .andExpect(status().isNoContent());
  }

  @Test
  void createFeatureConfig_negative() throws Exception {
    var feature = new FeatureConfig().feature(SEARCH_ALL_FIELDS).enabled(true);
    doPost(ApiEndpoints.featureConfigPath(), feature)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.feature", is(SEARCH_ALL_FIELDS.getValue())))
      .andExpect(jsonPath("$.enabled", is(true)));

    attemptPost(ApiEndpoints.featureConfigPath(), feature)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is("Feature configuration already exists")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("feature")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("search.all.fields")));

    doDelete(featureConfigPath(SEARCH_ALL_FIELDS)).andExpect(status().isNoContent());
  }

  @Test
  void createFeatureConfig_negative_bodyWithoutFields() throws Exception {
    attemptPost(ApiEndpoints.featureConfigPath(), new FeatureConfig())
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[*].parameters[*].key", containsInAnyOrder("enabled", "feature")));
  }

  @Test
  void updateFeatureConfig_notExists() throws Exception {
    var feature = new FeatureConfig().feature(SEARCH_ALL_FIELDS).enabled(true);
    attemptPut(featureConfigPath(SEARCH_ALL_FIELDS), feature)
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Feature configuration not found for id: search.all.fields")));
  }

  @Test
  void deleteUnknownFeature_notExists() throws Exception {
    attemptDelete(featureConfigPath(SEARCH_ALL_FIELDS))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Feature configuration not found for id: search.all.fields")));
  }

  @Test
  void getBrowseConfigs_positive() throws Exception {
    doGet(ApiEndpoints.browseConfigPath(BrowseType.INSTANCE_CLASSIFICATION))
      .andExpect(jsonPath("$.totalRecords", is(3)));
  }

  @Test
  void putBrowseConfigs_positive() throws Exception {
    var typeId1 = UUID.randomUUID();
    var typeId2 = UUID.randomUUID();
    var config = new BrowseConfig().id(BrowseOptionType.LC)
      .shelvingAlgorithm(ShelvingOrderAlgorithmType.DEFAULT)
      .addTypeIdsItem(typeId1).addTypeIdsItem(typeId2);

    var stub = mockClassificationTypes(okapi.wireMockServer(), typeId1, typeId2);

    doPut(ApiEndpoints.browseConfigPath(BrowseType.INSTANCE_CLASSIFICATION, BrowseOptionType.LC), config);

    var result = doGet(ApiEndpoints.browseConfigPath(BrowseType.INSTANCE_CLASSIFICATION))
      .andExpect(jsonPath("$.totalRecords", is(3)));

    var configCollection = parseResponse(result, BrowseConfigCollection.class);
    assertThat(configCollection.getConfigs())
      .hasSize(3)
      .contains(config);
    okapi.wireMockServer().removeStub(stub);
  }

  @Test
  void browseConfigs_synchronised_whenDeleteClassificationTypeEventReceived() {
    var typeId1 = UUID.randomUUID();
    var typeId2 = UUID.randomUUID();
    var config = new BrowseConfig().id(BrowseOptionType.LC)
      .shelvingAlgorithm(ShelvingOrderAlgorithmType.DEFAULT)
      .addTypeIdsItem(typeId2).addTypeIdsItem(typeId1);

    final var stub = mockClassificationTypes(okapi.wireMockServer(), typeId1, typeId2);

    doPut(ApiEndpoints.browseConfigPath(BrowseType.INSTANCE_CLASSIFICATION, BrowseOptionType.LC), config);

    kafkaTemplate.send(inventoryClassificationTopic(), typeId1.toString(), new ResourceEvent()
      .type(ResourceEventType.DELETE)
      .tenant(TENANT_ID)
      .resourceName(ResourceType.CLASSIFICATION_TYPE.getName())
      .old(mapOf(ID_FIELD, typeId1.toString()))
    );

    await().atMost(ONE_MINUTE).pollInterval(TWO_SECONDS).untilAsserted(() -> {
      var result = doGet(ApiEndpoints.browseConfigPath(BrowseType.INSTANCE_CLASSIFICATION));

      var configCollection = parseResponse(result, BrowseConfigCollection.class);
      for (BrowseConfig browseConfig : configCollection.getConfigs()) {
        if (browseConfig.getId() == BrowseOptionType.LC) {
          assertThat(browseConfig.getTypeIds())
            .hasSize(1)
            .containsExactly(typeId2);
        }
      }
    });

    okapi.wireMockServer().removeStub(stub);
  }

  @Test
  void referenceDataCacheInvalidates_whenClassificationTypeEventReceived() {
    var cacheKey = "cache-test-key";
    var referenceDataCache = Objects.requireNonNull(cacheManager.getCache(REFERENCE_DATA_CACHE));
    referenceDataCache.put(cacheKey, UUID.randomUUID());
    assertThat(referenceDataCache.get(cacheKey)).isNotNull();

    kafkaTemplate.send(inventoryClassificationTopic(), randomId(), new ResourceEvent()
      .resourceName(ResourceType.CLASSIFICATION_TYPE.getName())
    );

    await().atMost(ONE_MINUTE).pollInterval(TWO_SECONDS)
      .untilAsserted(() -> assertThat(referenceDataCache.get(cacheKey)).isNull());
  }

  @SneakyThrows
  private Map<String, Object> getIndexedInstanceById(String id) {
    final var searchRequest = new SearchRequest()
      .source(new SearchSourceBuilder().query(matchQuery("id", id)))
      .indices(getIndexName(ResourceType.INSTANCE, TENANT_ID));

    await().until(() -> elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT)
                          .getHits().getTotalHits().value > 0);

    return elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT).getHits()
      .getAt(0).getSourceAsMap();
  }

}
