package org.folio.search.converter.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * The StringLimitModule is a custom Jackson module designed to register
 * a specialized serializer for String values. This module incorporates
 * the {@link LimitedStringSerializer}, which enforces a byte size limit
 * on serialized strings to ensure they do not exceed a predefined limit
 * during serialization.
 */
public class StringLimitModule extends SimpleModule {

  public StringLimitModule() {
    addSerializer(String.class, new LimitedStringSerializer());
  }
}
