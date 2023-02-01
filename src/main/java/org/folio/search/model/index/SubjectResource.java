package org.folio.search.model.index;

import java.util.Set;
import lombok.Data;

@Data
public class SubjectResource {

  private String id;

  private String value;

  private String authorityId;

  private Set<String> instances;
}
