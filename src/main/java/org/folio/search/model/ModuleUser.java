package org.folio.search.model;

import lombok.Builder;
import lombok.Getter;
import lombok.With;

@Getter
@Builder
public class ModuleUser {
  private final String username;
  @With
  private final String token;
  private final String okapiUrl;
  private final String tenantId;
}
