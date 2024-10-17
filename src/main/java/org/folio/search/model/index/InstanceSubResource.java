package org.folio.search.model.index;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InstanceSubResource {
  private String tenantId;
  private Boolean shared;
  private int count;
  private List<String> typeId;
}
