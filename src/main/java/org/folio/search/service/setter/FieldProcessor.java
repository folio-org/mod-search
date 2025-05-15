package org.folio.search.service.setter;

/**
 * Generic interface for field processors.
 *
 * @param <T> generic type for input value
 * @param <R> generic type for return value
 */
public interface FieldProcessor<T, R> {

  Integer MAX_FIELD_VALUE_LENGTH = 32000;

  /**
   * Extract field value as {@link T} from {@link R} event body.
   *
   * @param eventBody event body as {@link R} object
   * @return extracted value as {@link T} object
   */
  R getFieldValue(T eventBody);
}
