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
@ConfigurationProperties(prefix = "application.query.properties")
public class SearchQueryConfigurationProperties {

  /**
   * Provides access for customization for call number browsing range offset to improve performance of the query.
   */
  private double callNumberRangeOffset = 0.5e18;

  /**
   * Provides range query limit multiplier as double.
   */
  private double rangeQueryLimitMultiplier = 2d;
}
