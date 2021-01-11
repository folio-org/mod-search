package org.folio.search.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import java.util.EnumSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides configuration and Spring beans for json path processing.
 */
@Configuration
public class JsonPathConfiguration {

  /**
   * Creates {@link JsonProvider} bean for json path processing.
   *
   * @param objectMapper {@link ObjectMapper} from Spring application context.
   * @return {@link JsonProvider} singleton bean
   */
  @Bean
  public JsonProvider jacksonJsonProvider(ObjectMapper objectMapper) {
    return new JacksonJsonProvider(objectMapper);
  }

  /**
   * Creates {@link MappingProvider} bean for json path processing.
   *
   * @param objectMapper {@link ObjectMapper} from Spring application context.
   * @return {@link MappingProvider} singleton bean
   */
  @Bean
  public MappingProvider jacksonMappingProvider(ObjectMapper objectMapper) {
    return new JacksonMappingProvider(objectMapper);
  }

  /**
   * Creates {@link ParseContext} for json path processing.
   *
   * @param jsonProvider {@link JsonProvider} bean from Spring application context.
   * @param mappingProvider {@link MappingProvider} bean from Spring application context.
   * @return {@link ParseContext} singleton bean.
   */
  @Bean
  public ParseContext jsonPath(JsonProvider jsonProvider, MappingProvider mappingProvider) {
    return JsonPath.using(jsonPathConfiguration(jsonProvider, mappingProvider));
  }

  private com.jayway.jsonpath.Configuration jsonPathConfiguration(
    JsonProvider jsonProvider, MappingProvider mappingProvider) {
    return com.jayway.jsonpath.Configuration.builder()
      .jsonProvider(jsonProvider)
      .mappingProvider(mappingProvider)
      .options(EnumSet.noneOf(Option.class))
      .build();
  }
}
