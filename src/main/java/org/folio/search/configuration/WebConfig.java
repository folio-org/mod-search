package org.folio.search.configuration;

import org.folio.search.domain.dto.RecordType;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(new StringToEnumConverter());
  }

  private static class StringToEnumConverter implements Converter<String, RecordType> {
    @Override
    public RecordType convert(String source) {
      return RecordType.valueOf(source.toUpperCase());
    }
  }
}
