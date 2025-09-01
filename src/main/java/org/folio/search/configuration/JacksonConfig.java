package org.folio.search.configuration;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT;
import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;
import org.folio.search.converter.jackson.StringLimitModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {

  @Bean
  public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
    return builder.createXmlMapper(false)
      .modules(new StringLimitModule())
      .featuresToEnable(ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
      .build();
  }

  @Bean
  public SmileMapper smileMapper() {
    return SmileMapper.builder()
      .addModule(new StringLimitModule())
      .enable(ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT)
      .enable(ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
      .build();
  }
}
