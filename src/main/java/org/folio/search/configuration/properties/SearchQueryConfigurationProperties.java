package org.folio.search.configuration.properties;

import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@ConfigurationProperties(prefix = "folio.query.properties")
public class SearchQueryConfigurationProperties {

  /**
   * Search request timeout.
   */
  private Duration requestTimeout = Duration.ofSeconds(25);
}
