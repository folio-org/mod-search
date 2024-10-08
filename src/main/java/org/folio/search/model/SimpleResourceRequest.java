package org.folio.search.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.search.model.types.ResourceType;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class SimpleResourceRequest implements ResourceRequest {

  private ResourceType resource;
  private String tenantId;
}
