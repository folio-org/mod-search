package org.folio.search.controller;

import static org.folio.search.utils.SearchUtils.X_OKAPI_TENANT_HEADER;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.languageConfig;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.persistence.EntityNotFoundException;
import org.folio.search.service.LanguageConfigService;
import org.folio.search.support.base.ApiEndpoints;
import org.folio.search.utils.types.UnitTest;
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

  @Autowired private MockMvc mockMvc;
  @MockBean private LanguageConfigService languageConfigService;

  @Test
  void updateLanguageConfig_positive() throws Exception {
    var code = "eng";
    var analyzer = "english";
    var languageConfig = languageConfig(code, analyzer);
    when(languageConfigService.update(code, languageConfig)).thenReturn(languageConfig(code, analyzer));

    mockMvc.perform(put(ApiEndpoints.languageConfig() + "/eng")
      .content(asJsonString(languageConfig))
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID)
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
      .header(X_OKAPI_TENANT_HEADER, TENANT_ID)
      .contentType(APPLICATION_JSON))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("total_records", is(1)))
      .andExpect(jsonPath("errors[0].code", is("not_found_error")))
      .andExpect(jsonPath("errors[0].type", is("EntityNotFoundException")))
      .andExpect(jsonPath("errors[0].message", is(errorMessage)));
  }
}
