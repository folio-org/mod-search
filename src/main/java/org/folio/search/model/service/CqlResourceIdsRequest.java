package org.folio.search.model.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.search.model.ResourceRequest;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class CqlResourceIdsRequest implements ResourceRequest {

  public static final String INSTANCE_ID_PATH = "id";
  public static final String HOLDING_ID_PATH = "holdings.id";

  /**
   * CQL query.
   */
  private String query;

  /**
   * Resource name.
   */
  private String resource;

  /**
   * Request tenant id.
   */
  private String tenantId;

  /**
   * Field path to the id field of the document.
   */
  private String sourceFieldPath;
}
