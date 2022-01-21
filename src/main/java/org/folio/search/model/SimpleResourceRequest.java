package org.folio.search.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class SimpleResourceRequest implements ResourceRequest {

  private String resource;
  private String tenantId;
}
