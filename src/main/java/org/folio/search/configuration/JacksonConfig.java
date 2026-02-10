package org.folio.search.configuration;

import static tools.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT;
import static tools.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT;
import static tools.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY;

import org.folio.search.converter.jackson.StringLimitModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.smile.SmileMapper;

@Configuration
public class JacksonConfig {

  @Bean
  public JsonMapper objectMapper() {
    return JsonMapper.builder()
      .addModule(new StringLimitModule())
      .enable(ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, ACCEPT_SINGLE_VALUE_AS_ARRAY)
      .build();
  }

  @Bean
  public SmileMapper smileMapper() {
    return SmileMapper.builder()
      .addModule(new StringLimitModule())
      .enable(ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, ACCEPT_SINGLE_VALUE_AS_ARRAY)
      .build();
  }
}
