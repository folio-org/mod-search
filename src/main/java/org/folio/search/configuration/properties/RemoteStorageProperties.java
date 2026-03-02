package org.folio.search.configuration.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties("folio.remote-storage")
public class RemoteStorageProperties {

  private String endpoint;
  private String region;
  private String bucket;
  private String accessKey;
  private String secretKey;
  private boolean awsSdk;
}
