package org.folio.search.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

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
  @Transient
  @With
  private String token;
  private String okapiUrl;
  private String tenantId;

  public SystemUser(SystemUser another) {
    this(another.getId(), another.getUsername(), another.getToken(), another.getOkapiUrl(),
      another.getTenantId());
  }
}
