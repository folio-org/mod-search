package org.folio.search.configuration.properties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("application.system-user")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolioSystemUserProperties {
  private String username;
  private String password;
  private String lastname;
  private String permissionsFilePath;
}
