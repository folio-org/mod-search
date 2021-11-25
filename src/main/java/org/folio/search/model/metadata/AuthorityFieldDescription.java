package org.folio.search.model.metadata;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AuthorityFieldDescription extends PlainFieldDescription {

  /**
   * Distinct type to split single to entity to multiple containing only common fields excluding all other fields marked
   * with other distinct type.
   */
  private String distinctType;

  /**
   * Heading type that should be set to the resource if field containing some values.
   */
  private String headingType;

  /**
   * Authorized, Reference or Auth/Ref type for divided authority record.
   */
  private String authRefType;
}
