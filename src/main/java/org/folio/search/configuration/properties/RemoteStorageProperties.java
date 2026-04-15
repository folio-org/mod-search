package org.folio.search.configuration.properties;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties("folio.remote-storage")
@ConditionalOnProperty(name = "folio.reindex.reindex-type", havingValue = "EXPORT")
public class RemoteStorageProperties {

  @NotNull(message = "Remote storage endpoint is required")
  private String endpoint;
  @NotNull(message = "Remote storage region is required")
  private String region;
  @NotNull(message = "Remote storage bucket is required")
  private String bucket;
  private String accessKey;
  private String secretKey;
  private boolean awsSdk = true;
}
