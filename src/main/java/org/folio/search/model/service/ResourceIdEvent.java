package org.folio.search.model.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.search.model.types.IndexActionType;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class ResourceIdEvent {

  /**
   * Resource id.
   */
  private String id;

  /**
   * Resource type.
   */
  private String type;

  /**
   * Tenant id.
   */
  private String tenant;

  /**
   * Index action type - index or delete.
   */
  private IndexActionType action;
}
