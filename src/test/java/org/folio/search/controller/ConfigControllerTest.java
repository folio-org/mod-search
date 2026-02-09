package org.folio.search.controller;

import static java.util.UUID.randomUUID;
import static org.folio.search.domain.dto.TenantConfiguredFeature.SEARCH_ALL_FIELDS;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.utils.JsonTestUtils.asJsonString;
import static org.folio.support.utils.TestUtils.languageConfig;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseConfigCollection;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.FeatureConfigs;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.exception.ValidationException;
import org.folio.search.service.consortium.BrowseConfigServiceDecorator;
import org.folio.search.service.consortium.FeatureConfigServiceDecorator;
import org.folio.search.service.consortium.LanguageConfigServiceDecorator;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.type.UnitTest;
import org.folio.support.base.ApiEndpoints;
import org.folio.support.config.TestNoOpCacheConfig;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@UnitTest
@WebMvcTest(ConfigController.class)
@Import({ApiExceptionHandler.class, TestNoOpCacheConfig.class})
class ConfigControllerTest {

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private LanguageConfigServiceDecorator languageConfigService;
  @MockitoBean
  private FeatureConfigServiceDecorator featureConfigService;
  @MockitoBean
  private BrowseConfigServiceDecorator browseConfigService;

  @Test
  void createLanguageConfig_positive() throws Exception {
    var code = "eng";
    var languageConfig = languageConfig(code);
    when(languageConfigService.create(languageConfig)).thenReturn(languageConfig);

    mockMvc.perform(post(ApiEndpoints.languageConfigPath())
        .content(asJsonString(languageConfig))
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("code", is(code)));
  }

  @Test
  void getAllLanguageConfigs_positive() throws Exception {
    var languageConfigs = new LanguageConfigs()
      .addLanguageConfigsItem(languageConfig("eng", "english"))
      .totalRecords(1);

    when(languageConfigService.getAll()).thenReturn(languageConfigs);
    mockMvc.perform(get(ApiEndpoints.languageConfigPath())
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.languageConfigs[0].code", is("eng")))
      .andExpect(jsonPath("$.languageConfigs[0].languageAnalyzer", is("english")));
  }

  @Test
  void updateLanguageConfig_positive() throws Exception {
    var code = "eng";
    var analyzer = "english";
    var languageConfig = languageConfig(code, analyzer);
    when(languageConfigService.update(code, languageConfig)).thenReturn(languageConfig(code, analyzer));

    mockMvc.perform(put(ApiEndpoints.languageConfigPath() + "/eng")
        .content(asJsonString(languageConfig))
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("code", is(code)))
      .andExpect(jsonPath("languageAnalyzer", is(analyzer)));
  }

  @Test
  void updateLanguageConfig_negative_codeByIdIsNotFound() throws Exception {
    var code = "eng";
    var languageConfig = languageConfig(code);
    var errorMessage = "Language config not found for code: " + code;

    when(languageConfigService.update(code, languageConfig)).thenThrow(new EntityNotFoundException(errorMessage));

    mockMvc.perform(put(ApiEndpoints.languageConfigPath() + "/eng")
        .content(asJsonString(languageConfig))
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("total_records", is(1)))
      .andExpect(jsonPath("errors[0].code", is("not_found_error")))
      .andExpect(jsonPath("errors[0].type", is("EntityNotFoundException")))
      .andExpect(jsonPath("errors[0].message", is(errorMessage)));
  }

  @Test
  void updateLanguageConfig_negative_invalidCode() throws Exception {
    mockMvc.perform(post(ApiEndpoints.languageConfigPath())
        .content(asJsonString(languageConfig("english", "english")))
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("total_records", is(1)))
      .andExpect(jsonPath("errors[0].code", is("validation_error")))
      .andExpect(jsonPath("errors[0].type", is("MethodArgumentNotValidException")))
      .andExpect(jsonPath("errors[0].parameters[0].key", is("code")))
      .andExpect(jsonPath("errors[0].parameters[0].value", is("english")));
  }

  @Test
  void updateLanguageConfig_negative_invalidLanguageAnalyzer() throws Exception {
    var languageConfig = languageConfig("ita");
    when(languageConfigService.create(languageConfig)).thenThrow(
      new ValidationException("Language has no analyzer available", "code", "ita"));

    mockMvc.perform(post(ApiEndpoints.languageConfigPath())
        .content(asJsonString(languageConfig))
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(jsonPath("total_records", is(1)))
      .andExpect(jsonPath("errors[0].code", is("validation_error")))
      .andExpect(jsonPath("errors[0].type", is("ValidationException")))
      .andExpect(jsonPath("errors[0].parameters[0].key", is("code")))
      .andExpect(jsonPath("errors[0].parameters[0].value", is("ita")));
  }

  @Test
  void deleteLanguageConfig_positive() throws Exception {
    var languageCode = "eng";
    doNothing().when(languageConfigService).delete(languageCode);
    mockMvc.perform(delete(ApiEndpoints.languageConfigPath(languageCode))
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }

  @Test
  void getAllFeatures_positive() throws Exception {
    var feature = new FeatureConfig().feature(SEARCH_ALL_FIELDS).enabled(true);
    when(featureConfigService.getAll()).thenReturn(new FeatureConfigs().features(List.of(feature)).totalRecords(1));

    var request = get(ApiEndpoints.featureConfigPath())
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .contentType(APPLICATION_JSON);

    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.features[0].feature", is(SEARCH_ALL_FIELDS.getValue())))
      .andExpect(jsonPath("$.features[0].enabled", is(true)));
  }

  @Test
  void saveFeatureConfiguration_positive() throws Exception {
    var feature = new FeatureConfig().feature(SEARCH_ALL_FIELDS).enabled(true);
    when(featureConfigService.create(feature)).thenReturn(feature);

    var request = post(ApiEndpoints.featureConfigPath())
      .content(asJsonString(feature))
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .contentType(APPLICATION_JSON);

    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.feature", is(SEARCH_ALL_FIELDS.getValue())))
      .andExpect(jsonPath("$.enabled", is(true)));
  }

  @Test
  void saveFeatureConfiguration_negative_alreadyExists() throws Exception {
    var feature = new FeatureConfig().feature(SEARCH_ALL_FIELDS).enabled(true);
    when(featureConfigService.create(feature)).thenThrow(new RequestValidationException(
      "Feature configuration already exists", "feature", SEARCH_ALL_FIELDS.getValue()));

    var request = post(ApiEndpoints.featureConfigPath())
      .content(asJsonString(feature))
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .contentType(APPLICATION_JSON);

    mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("feature")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(SEARCH_ALL_FIELDS.getValue())));
  }

  @Test
  void saveFeatureConfiguration_negative_invalidFeatureName() throws Exception {
    var request = post(ApiEndpoints.featureConfigPath())
      .content(asJsonString(mapOf("feature", "unknown-feature-name", "enabled", true)))
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .contentType(APPLICATION_JSON);

    mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].type", is("IllegalArgumentException")))
      .andExpect(jsonPath("$.errors[0].message", is("Unexpected value 'unknown-feature-name'")));
  }

  @Test
  void saveFeatureConfiguration_negative_unexpectedBooleanValue() throws Exception {
    var request = post(ApiEndpoints.featureConfigPath())
      .content(asJsonString(mapOf("feature", SEARCH_ALL_FIELDS.getValue(), "enabled", "unknown")))
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .contentType(APPLICATION_JSON);

    mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].type", is("HttpMessageNotReadableException")))
      .andExpect(jsonPath("$.errors[0].message", containsString(
        "SON parse error: Cannot deserialize value of type `java.lang.Boolean` from String")));
  }

  @Test
  void updateFeatureConfiguration_positive() throws Exception {
    var feature = new FeatureConfig().feature(SEARCH_ALL_FIELDS).enabled(true);
    when(featureConfigService.update(SEARCH_ALL_FIELDS, feature)).thenReturn(feature);

    var request = put(ApiEndpoints.featureConfigPath(SEARCH_ALL_FIELDS))
      .content(asJsonString(feature))
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .contentType(APPLICATION_JSON);

    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.feature", is(SEARCH_ALL_FIELDS.getValue())))
      .andExpect(jsonPath("$.enabled", is(true)));
  }

  @Test
  void deleteFeatureConfigurationById_positive() throws Exception {
    doNothing().when(featureConfigService).delete(SEARCH_ALL_FIELDS);
    var request = delete(ApiEndpoints.featureConfigPath(SEARCH_ALL_FIELDS))
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .contentType(APPLICATION_JSON);

    mockMvc.perform(request).andExpect(status().isNoContent());
  }

  @ParameterizedTest
  @EnumSource(BrowseType.class)
  void getBrowseConfigs_positive(BrowseType type) throws Exception {
    var config = new BrowseConfig().id(BrowseOptionType.LC).shelvingAlgorithm(ShelvingOrderAlgorithmType.LC)
      .typeIds(List.of(randomUUID(), randomUUID()));
    when(browseConfigService.getConfigs(type))
      .thenReturn(new BrowseConfigCollection().addConfigsItem(config).totalRecords(1));

    var request = get(ApiEndpoints.browseConfigPath(type))
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .contentType(APPLICATION_JSON);

    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.configs[0].id", is(BrowseOptionType.LC.getValue())))
      .andExpect(jsonPath("$.configs[0].shelvingAlgorithm", is(ShelvingOrderAlgorithmType.LC.getValue())))
      .andExpect(jsonPath("$.configs[0].typeIds[*]",
        containsInAnyOrder(config.getTypeIds().stream().map(UUID::toString).toArray())));
  }

  @ParameterizedTest
  @EnumSource(BrowseType.class)
  void putBrowseConfig_positive(BrowseType type) throws Exception {
    var config = new BrowseConfig().id(BrowseOptionType.LC).shelvingAlgorithm(ShelvingOrderAlgorithmType.LC)
      .typeIds(List.of(randomUUID(), randomUUID()));
    doNothing().when(browseConfigService).upsertConfig(type, BrowseOptionType.LC, config);

    var request = put(ApiEndpoints.browseConfigPath(type, BrowseOptionType.LC))
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .contentType(APPLICATION_JSON)
      .content(asJsonString(config));

    mockMvc.perform(request).andExpect(status().isOk())
      .andExpect(MockMvcResultMatchers.content().string(Matchers.emptyOrNullString()));
  }
}
