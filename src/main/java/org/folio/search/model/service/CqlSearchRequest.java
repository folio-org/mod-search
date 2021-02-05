package org.folio.search.model.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CQL based search request model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class CqlSearchRequest {

  /**
   * Resource name.
   */
  private String resource;

  /**
   * CQL query.
   */
  private String cqlQuery;

  /**
   * Request tenant id.
   */
  private String tenantId;

  /**
   * Page size.
   */
  @Builder.Default
  private Integer limit = 100;

  /**
   * Page number.
   */
  @Builder.Default
  private Integer offset = 0;

  @Builder.Default
  private boolean expandAll = false;
}
