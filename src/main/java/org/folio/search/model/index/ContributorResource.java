package org.folio.search.model.index;

import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContributorResource {

  private String id;

  private String name;

  private String contributorNameTypeId;

  private String authorityId;

  private Set<InstanceSubResource> instances;
}
