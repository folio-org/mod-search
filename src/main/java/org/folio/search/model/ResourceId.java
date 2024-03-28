package org.folio.search.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceId {

  private String id;

  public ResourceId id(String id) {
    this.id = id;
    return this;
  }
}
