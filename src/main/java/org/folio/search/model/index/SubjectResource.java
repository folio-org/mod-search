package org.folio.search.model.index;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectResource {

  private String id;

  private String value;

  private String authorityId;

  private Set<InstanceSubResource> instances;
}
