package org.folio.search.controller;

import static org.awaitility.Awaitility.await;
import static org.folio.search.domain.dto.TenantConfiguredFeature.SEARCH_ALL_FIELDS;
import static org.folio.search.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.search.support.base.ApiEndpoints.featureConfig;
import static org.folio.search.support.base.ApiEndpoints.languageConfig;
import static org.folio.search.utils.SearchConverterUtils.getMapValueByPath;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.opensearch.index.query.QueryBuilders.matchQuery;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.support.base.ApiEndpoints;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.TestUtils;
import org.folio.search.utils.types.IntegrationTest;
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
    parseResponse(doGet(languageConfig()), LanguageConfigs.class)
      .getLanguageConfigs()
      .forEach(config -> doDelete(languageConfig() + "/{code}", config.getCode()));
  }

  @Test
  void canCreateLanguageConfig() throws Exception {
    final var languageCode = "eng";

    doPost(languageConfig(), new LanguageConfig().code(languageCode));

    doGet(languageConfig())
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("languageConfigs[0].code", is(languageCode)));
  }

  @Test
  void canCreateLanguageConfigWithCustomAnalyzer() throws Exception {
    final var languageCode = "kor";
    final var analyzer = "nori";

    doPost(languageConfig(), TestUtils.languageConfig(languageCode, analyzer));

    doGet(languageConfig())
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("languageConfigs[0].code", is(languageCode)))
      .andExpect(jsonPath("languageConfigs[0].languageAnalyzer", is(analyzer)));

    var newAnalyzer = "seunjeon_analyzer";
    doPut(languageConfig() + "/kor", TestUtils.languageConfig(languageCode, newAnalyzer))
      .andExpect(jsonPath("languageAnalyzer", is(newAnalyzer)));

    doGet(languageConfig())
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("languageConfigs[0].code", is(languageCode)))
      .andExpect(jsonPath("languageConfigs[0].languageAnalyzer", is(newAnalyzer)));
  }

  @Test
  void cannotUpdateNonExistingLanguageConfig() throws Exception {
    final var languageCode = "kor";
    final var analyzer = "nori";

    doPost(languageConfig(), TestUtils.languageConfig(languageCode, analyzer));

    doGet(languageConfig())
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("languageConfigs[0].code", is(languageCode)))
      .andExpect(jsonPath("languageConfigs[0].languageAnalyzer", is(analyzer)));
  }

  @Test
  void cannotAddLanguageIfNoAnalyzer() throws Exception {
    attemptPost(languageConfig(), new LanguageConfig().code("ukr"))
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

    doPost(languageConfig(), language);

    doDelete(languageConfig() + "/fre")
      .andExpect(status().isNoContent());
  }

  @Test
  @Disabled("Check how to validate configured languages if values are excluded from source (dev setting for _source)")
  @SuppressWarnings("unchecked")
  void shouldUseConfiguredLanguagesDuringMapping() {
    final List<String> languageCodes = List.of("eng", "rus");
    for (String languageCode : languageCodes) {
      doPost(languageConfig(), new LanguageConfig().code(languageCode));
    }

    var newInstance = new Instance()
      .id(randomId())
      .languages(List.of("eng", "rus", "fre"))
      .title("This is title");

    inventoryApi.createInstance(TENANT_ID, newInstance);

    final var indexedInstance = getIndexedInstanceById(newInstance.getId());

    assertThat((Map<String, Object>) getMapValueByPath("title", indexedInstance), aMapWithSize(3));
    assertThat(getMapValueByPath("title.eng", indexedInstance), is(newInstance.getTitle()));
    assertThat(getMapValueByPath("title.rus", indexedInstance), is(newInstance.getTitle()));
    assertThat(getMapValueByPath("title.src", indexedInstance), is(newInstance.getTitle()));
    assertThat(getMapValueByPath("title.fre", indexedInstance), nullValue());
  }

  @Test
  void featureConfigurationWorkflow_positive() throws Exception {
    var feature = new FeatureConfig().feature(SEARCH_ALL_FIELDS).enabled(true);
    doPost(ApiEndpoints.featureConfig(), feature)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.feature", is(SEARCH_ALL_FIELDS.getValue())))
      .andExpect(jsonPath("$.enabled", is(true)));

    var featureToUpdate = new FeatureConfig().feature(SEARCH_ALL_FIELDS).enabled(false);
    doPut(ApiEndpoints.featureConfig(SEARCH_ALL_FIELDS), featureToUpdate)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.feature", is(SEARCH_ALL_FIELDS.getValue())))
      .andExpect(jsonPath("$.enabled", is(false)));

    doDelete(featureConfig(SEARCH_ALL_FIELDS))
      .andExpect(status().isNoContent());
  }

  @Test
  void createFeatureConfig_negative() throws Exception {
    var feature = new FeatureConfig().feature(SEARCH_ALL_FIELDS).enabled(true);
    doPost(featureConfig(), feature)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.feature", is(SEARCH_ALL_FIELDS.getValue())))
      .andExpect(jsonPath("$.enabled", is(true)));

    attemptPost(featureConfig(), feature)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].message", is("Feature configuration already exists")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("feature")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("search.all.fields")));

    doDelete(featureConfig(SEARCH_ALL_FIELDS)).andExpect(status().isNoContent());
  }

  @Test
  void createFeatureConfig_negative_bodyWithoutFields() throws Exception {
    attemptPost(featureConfig(), new FeatureConfig())
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[*].parameters[*].key", containsInAnyOrder("enabled", "feature")));
  }

  @Test
  void updateFeatureConfig_notExists() throws Exception {
    var feature = new FeatureConfig().feature(SEARCH_ALL_FIELDS).enabled(true);
    attemptPut(featureConfig(SEARCH_ALL_FIELDS), feature)
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Feature configuration not found for id: search.all.fields")));
  }

  @Test
  void deleteUnknownFeature_notExists() throws Exception {
    attemptDelete(featureConfig(SEARCH_ALL_FIELDS))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Feature configuration not found for id: search.all.fields")));
  }

  @SneakyThrows
  private Map<String, Object> getIndexedInstanceById(String id) {
    final var searchRequest = new SearchRequest()
      .source(new SearchSourceBuilder().query(matchQuery("id", id)))
      .indices(getIndexName(INSTANCE_RESOURCE, TENANT_ID));

    await().until(() -> elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT)
      .getHits().getTotalHits().value > 0);

    return elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT).getHits()
      .getAt(0).getSourceAsMap();
  }
}
