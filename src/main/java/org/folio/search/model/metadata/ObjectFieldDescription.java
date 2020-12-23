package org.folio.search.model.metadata;

import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * POJO class for specifying a object field description for search engine.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ObjectFieldDescription extends FieldDescription {

  /**
   * Map with inner fields for object field.
   */
  private Map<String, FieldDescription> properties;
}
