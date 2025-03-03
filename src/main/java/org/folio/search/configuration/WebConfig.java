package org.folio.search.configuration;

import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
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
    registry.addConverter(new StringToBrowseTypeConverter());
    registry.addConverter(new StringToBrowseOptionTypeConverter());
    registry.addConverter(new StringToSortOrderTypeConverter());
  }

  private static final class StringToRecordTypeEnumConverter implements Converter<String, RecordType> {
    @Override
    public RecordType convert(String source) {
      return RecordType.valueOf(source.toUpperCase().replace('-', '_'));
    }
  }

  private static final class StringToBrowseTypeConverter implements Converter<String, BrowseType> {
    @Override
    public BrowseType convert(String source) {
      return BrowseType.fromValue(source.toLowerCase());
    }
  }

  private static final class StringToBrowseOptionTypeConverter implements Converter<String, BrowseOptionType> {
    @Override
    public BrowseOptionType convert(String source) {
      return BrowseOptionType.fromValue(source.toLowerCase());
    }
  }

  private static final class StringToSortOrderTypeConverter implements Converter<String, SortOrder> {
    @Override
    public SortOrder convert(String source) {
      return SortOrder.fromValue(source.toLowerCase());
    }
  }
}
