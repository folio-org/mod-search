package org.folio.search.model.index;

import java.util.Set;
import lombok.Data;

@Data
public class ContributorResource {

  private String id;

  private String name;

  private Set<String> contributorTypeId;

  private String contributorNameTypeId;

  private String authorityId;

  private Set<String> instances;
}
