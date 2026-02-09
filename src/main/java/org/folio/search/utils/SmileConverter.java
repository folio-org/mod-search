package org.folio.search.utils;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SerializationException;
import org.opensearch.core.common.bytes.BytesArray;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.smile.SmileMapper;

/**
 * A Spring component for serialization and deserialization operations basing on jackson smileMapper.
 */
@Log4j2
@Component
public class SmileConverter {

  public static final String SERIALIZATION_ERROR_MSG_TEMPLATE = "Failed to serialize value [message: %s]";

  private final SmileMapper smileMapper;

  public SmileConverter(SmileMapper smileMapper) {
    this.smileMapper = smileMapper;
  }

  /**
   * Converts passed {@link Object} value to smile string.
   *
   * @param value value to convert
   * @return smile value as {@link String}.
   */
  public BytesArray toSmile(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return new BytesArray(smileMapper.writeValueAsBytes(value));
    } catch (JacksonException e) {
      throw new SerializationException(String.format(SERIALIZATION_ERROR_MSG_TEMPLATE, e.getMessage()));
    }
  }
}
