package org.folio.search.model.index;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InstanceSubResource {
  private String instanceId;
  private String tenantId;
  private Boolean shared;
}
