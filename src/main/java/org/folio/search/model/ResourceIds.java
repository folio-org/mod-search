package org.folio.search.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceIds {

  private Integer totalRecords;
  private List<ResourceId> ids;

  public ResourceIds totalRecords(Integer totalRecords) {
    this.totalRecords = totalRecords;
    return this;
  }

  public ResourceIds ids(List<ResourceId> ids) {
    this.ids = ids;
    return this;
  }
}
