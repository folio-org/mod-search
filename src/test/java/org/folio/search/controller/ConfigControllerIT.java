package org.folio.search.controller;

import static org.awaitility.Awaitility.await;
import static org.folio.search.support.base.ApiEndpoints.languageConfig;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.INVENTORY_INSTANCE_TOPIC;
import static org.folio.search.utils.TestUtils.eventBody;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.sample.InstanceBuilder;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

@IntegrationTest
class ConfigControllerIT extends BaseIntegrationTest {
  @Autowired
  private KafkaTemplate<String, Object> kafkaTemplate;

  @BeforeEach
  void removeConfigs() {
    var languageConfigIds = parseResponse(doGet(languageConfig()), LanguageConfigs.class)
      .getLanguageConfigs().stream()
      .map(LanguageConfig::getId)
      .collect(Collectors.toSet());

    for (var id : languageConfigIds) {
      doDelete(languageConfig() + "/{id}", id);
    }
  }

  @Test
  void canCreateLanguageConfig() throws Exception {
    final String languageCode = "eng";

    doPost(languageConfig(), new LanguageConfig().code(languageCode));

    doGet(languageConfig())
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("languageConfigs[0].code", is(languageCode)));
  }

  @Test
  void cannotHaveMoreThan5LanguageConfigs() throws Exception {
    final List<String> languageCodes = List.of("eng", "ara", "rus", "ger", "spa");

    for (String languageCode : languageCodes) {
      doPost(languageConfig(), new LanguageConfig().code(languageCode));
    }

    attemptPost(languageConfig(), new LanguageConfig().code("heb"))
      .andExpect(status().is(422))
      .andExpect(jsonPath("errors[0].parameters.key", is("code")))
      .andExpect(jsonPath("errors[0].parameters.value", is("heb")))
      .andExpect(jsonPath("errors[0].message",
        is("Tenant is allowed to have only 5 languages configured")));

    doGet(languageConfig())
      .andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("languageConfigs[*].code", is(languageCodes)));
  }

  @Test
  void cannotAddLanguageIfNoAnalyzer() throws Exception {
    attemptPost(languageConfig(), new LanguageConfig().code("ukr"))
      .andExpect(status().is(422))
      .andExpect(jsonPath("errors[0].parameters.key", is("code")))
      .andExpect(jsonPath("errors[0].parameters.value", is("ukr")))
      .andExpect(jsonPath("errors[0].message",
        is("Language has no analyzer available")));
  }

  @Test
  void canRemoveLanguageConfig() {
    final LanguageConfig language = new LanguageConfig()
      .code("fre")
      .id(UUID.randomUUID().toString());

    doPost(languageConfig(), language);

    doDelete(languageConfig() + "/{id}", language.getId());
  }

  @Test
  void shouldUseConfiguredLanguagesDuringMapping() {
    final List<String> languageCodes = List.of("eng", "rus");
    for (String languageCode : languageCodes) {
      doPost(languageConfig(), new LanguageConfig().code(languageCode));
    }

    var newInstance = InstanceBuilder.builder()
      .languages(languageCodes)
      .title("This is title")
      .build();

    kafkaTemplate.send(INVENTORY_INSTANCE_TOPIC, newInstance.getId().toString(),
      eventBody(INSTANCE_RESOURCE, newInstance));

    await()
      .untilAsserted(() -> doGet(searchInstancesByQuery("id==\"{id}\""), newInstance.getId())
        .andExpect(jsonPath("totalRecords", is(1)))
        .andExpect(jsonPath("instances[0].id", is(newInstance.getId().toString())))
        .andExpect(jsonPath("instances[0].title.src", is(newInstance.getTitle())))
        .andExpect(jsonPath("instances[0].title.eng", is(newInstance.getTitle())))
        .andExpect(jsonPath("instances[0].title.rus", is(newInstance.getTitle()))));
  }
}
