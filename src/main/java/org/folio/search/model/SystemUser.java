package org.folio.search.model;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemUser {
  private UUID id;
  private String username;
  private String okapiToken;
  private String okapiUrl;
  private String tenantId;

  public boolean hasNoToken() {
    return StringUtils.isBlank(okapiToken);
  }
}
