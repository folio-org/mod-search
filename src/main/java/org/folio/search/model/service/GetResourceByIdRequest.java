package org.folio.search.model.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(staticName = "of")
public class GetResourceByIdRequest {

  private String resource;
  private String tenantId;
  private String resourceId;
}
