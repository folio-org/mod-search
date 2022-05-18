package org.folio.search.model.index;

import java.util.List;
import java.util.Set;

import lombok.Data;

@Data
public class ContributorResource {

  private String id;

  private String name;

  private Set<String> contributorTypeId;

  private String contributorNameTypeId;

  private Set<String> instances;
}
