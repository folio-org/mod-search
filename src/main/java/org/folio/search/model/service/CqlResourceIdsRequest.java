package org.folio.search.model.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.types.ResourceType;

@Data
@RequiredArgsConstructor(staticName = "of")
public class CqlResourceIdsRequest implements ResourceRequest {

  public static final String INSTANCE_ID_PATH = "id";
  public static final String AUTHORITY_ID_PATH = "id";
  public static final String HOLDINGS_ID_PATH = "holdings.id";

  /**
   * Resource name.
   */
  private final ResourceType resource;

  /**
   * Request tenant id.
   */
  private final String tenantId;

  /**
   * A CQL query string with search conditions.
   */
  private final String query;

  /**
   * Field path to the id field of the document.
   */
  private final String sourceFieldPath;
}
