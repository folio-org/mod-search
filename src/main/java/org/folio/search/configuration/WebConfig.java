package org.folio.search.configuration;

import org.folio.search.domain.dto.CallNumberType;
import org.folio.search.domain.dto.RecordType;
import org.folio.search.domain.dto.SortOrder;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(new StringToRecordTypeEnumConverter());
    registry.addConverter(new StringToCallNumberTypeEnumConverter());
    registry.addConverter(new StringToSortOrderTypeConverter());
  }

  private static final class StringToRecordTypeEnumConverter implements Converter<String, RecordType> {
    @Override
    public RecordType convert(String source) {
      return RecordType.valueOf(source.toUpperCase());
    }
  }

  private static final class StringToCallNumberTypeEnumConverter implements Converter<String, CallNumberType> {
    @Override
    public CallNumberType convert(String source) {
      return CallNumberType.valueOf(source.toUpperCase());
    }
  }

  private static final class StringToSortOrderTypeConverter implements Converter<String, SortOrder> {
    @Override
    public SortOrder convert(String source) {
      return SortOrder.fromValue(source.toLowerCase());
    }
  }
}
