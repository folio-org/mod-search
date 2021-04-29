package org.folio.search.controller;

import static org.awaitility.Awaitility.await;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.folio.search.support.base.ApiEndpoints.languageConfig;
import static org.folio.search.utils.SearchConverterUtils.getMapValueByPath;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.TestUtils;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ConfigControllerIT extends BaseIntegrationTest {

  @Autowired
  private RestHighLevelClient elasticsearchClient;

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

  @SneakyThrows
  private Map<String, Object> getIndexedInstanceById(String id) {
    final var searchRequest = new SearchRequest()
      .routing(TENANT_ID)
      .source(new SearchSourceBuilder().query(matchQuery("id", id)))
      .indices(getElasticsearchIndexName(INSTANCE_RESOURCE, TENANT_ID));

    await().until(() -> elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT)
      .getHits().getTotalHits().value > 0);

    return elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT).getHits()
      .getAt(0).getSourceAsMap();
  }
}
