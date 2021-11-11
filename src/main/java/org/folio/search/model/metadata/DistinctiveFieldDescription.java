package org.folio.search.model.metadata;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DistinctiveFieldDescription extends PlainFieldDescription {

  /**
   * Heading type that should be set to the resource if field containing some values.
   */
  private String headingType;

  /**
   * Distinct type to split single to entity to multiple containing only common fields excluding all other fields marked
   * with other distinct type.
   */
  private String distinctType;
}
