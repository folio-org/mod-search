package org.folio.search.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "system_user")
public class SystemUser {
  @Id
  private String id;
  private String username;
  private String token;
  private String okapiUrl;
  private String tenantId;

  public boolean hasToken() {
    return StringUtils.isNotBlank(token);
  }
}
