package org.folio.search.model.metadata;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SearchFieldDescriptor extends PlainFieldDescription {
  /**
   * Name of a Spring bean that is used to generate value for the field.
   *
   * @see org.folio.search.service.setter.FieldProcessor
   */
  private String processor;
}
