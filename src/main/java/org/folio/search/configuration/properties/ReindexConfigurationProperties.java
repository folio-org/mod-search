package org.folio.search.configuration.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@ConfigurationProperties(prefix = "folio.reindex")
public class ReindexConfigurationProperties {

  /**
   * Defines number of locations to retrieve per inventory http request on locations reindex process.
   */
  private Integer locationBatchSize = 1_000;
}
