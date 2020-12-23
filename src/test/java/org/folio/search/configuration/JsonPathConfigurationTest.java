package org.folio.search.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class JsonPathConfigurationTest {

  @InjectMocks
  private JsonPathConfiguration jsonPathConfiguration;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void jacksonJsonProviderBean() {
    var jsonProvider = jsonPathConfiguration.jacksonJsonProvider(objectMapper);
    assertThat(jsonProvider).isNotNull();
  }

  @Test
  void jacksonMappingProvider() {
    var mappingProvider = jsonPathConfiguration.jacksonMappingProvider(objectMapper);
    assertThat(mappingProvider).isNotNull();
  }

  @Test
  void name() {
    var jsonProvider = jsonPathConfiguration.jacksonJsonProvider(objectMapper);
    var mappingProvider = jsonPathConfiguration.jacksonMappingProvider(objectMapper);
    var jsonPath = jsonPathConfiguration.jsonPath(jsonProvider, mappingProvider);
    assertThat(jsonPath).isNotNull();
  }
}