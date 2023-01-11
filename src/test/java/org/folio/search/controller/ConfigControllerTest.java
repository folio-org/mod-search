package org.folio.search.controller;

import static org.folio.search.domain.dto.TenantConfiguredFeature.SEARCH_ALL_FIELDS;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.languageConfig;
import static org.folio.search.utils.TestUtils.mapOf;
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

import java.util.List;
import javax.persistence.EntityNotFoundException;
import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.FeatureConfigs;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.exception.ValidationException;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.LanguageConfigService;
import org.folio.search.support.base.ApiEndpoints;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@Import(ApiExceptionHandler.class)
@WebMvcTest(ConfigController.class)
class ConfigControllerTest {

  @Autowired
  private MockMvc mockMvc;
  @MockBean
  private LanguageConfigService languageConfigService;
  @MockBean
  private FeatureConfigService featureConfigService;

  @Test
  void createLanguageConfig_positive() throws Exception {
    var code = "eng";
    var languageConfig = languageConfig(code);
    when(languageConfigService.create(languageConfig)).thenReturn(languageConfig);

    mockMvc.perform(post(ApiEndpoints.languageConfig())
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
    mockMvc.perform(get(ApiEndpoints.languageConfig())
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

    mockMvc.perform(put(ApiEndpoints.languageConfig() + "/eng")
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

    mockMvc.perform(put(ApiEndpoints.languageConfig() + "/eng")
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
    mockMvc.perform(post(ApiEndpoints.languageConfig())
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

    mockMvc.perform(post(ApiEndpoints.languageConfig())
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
    mockMvc.perform(delete(ApiEndpoints.languageConfig(languageCode))
        .header(XOkapiHeaders.TENANT, TENANT_ID)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }

  @Test
  void getAllFeatures_positive() throws Exception {
    var feature = new FeatureConfig().feature(SEARCH_ALL_FIELDS).enabled(true);
    when(featureConfigService.getAll()).thenReturn(new FeatureConfigs().features(List.of(feature)).totalRecords(1));

    var request = get(ApiEndpoints.featureConfig())
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

    var request = post(ApiEndpoints.featureConfig())
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

    var request = post(ApiEndpoints.featureConfig())
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
    var request = post(ApiEndpoints.featureConfig())
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
    var request = post(ApiEndpoints.featureConfig())
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

    var request = put(ApiEndpoints.featureConfig(SEARCH_ALL_FIELDS))
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
    var request = delete(ApiEndpoints.featureConfig(SEARCH_ALL_FIELDS))
      .header(XOkapiHeaders.TENANT, TENANT_ID)
      .contentType(APPLICATION_JSON);

    mockMvc.perform(request).andExpect(status().isNoContent());
  }
}
