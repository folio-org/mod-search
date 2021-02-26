package org.folio.search.model.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
