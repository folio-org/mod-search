package org.folio.search.configuration.jpa;

import static java.util.Collections.emptyList;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {
  private static final String SPLIT_CHAR = ";";

  @Override
  public String convertToDatabaseColumn(List<String> stringList) {
    return stringList != null ? String.join(SPLIT_CHAR, stringList) : null;
  }

  @Override
  public List<String> convertToEntityAttribute(String string) {
    return StringUtils.isNotBlank(string) ? Arrays.asList(string.split(SPLIT_CHAR)) : emptyList();
  }
}
