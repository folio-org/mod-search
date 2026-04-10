package org.folio.search.configuration.properties;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@NoArgsConstructor
@ConfigurationProperties(prefix = "folio.streaming-client")
public class StreamingClientProperties {

  private int pageSize = 500;
  private int streamLimit = 200_000;
  private int connectTimeoutMs = 10_000;
  private int readTimeoutMs = 300_000;
}
