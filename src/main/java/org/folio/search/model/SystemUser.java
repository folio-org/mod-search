package org.folio.search.model;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemUser {
  private UUID id;
  private String username;
  private String password;
  private String okapiToken;
  private String okapiUrl;
  private String tenantId;
}
