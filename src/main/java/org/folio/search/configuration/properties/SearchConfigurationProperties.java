package org.folio.search.configuration.properties;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import lombok.Data;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "application.search-config")
public class SearchConfigurationProperties {

  /**
   * Provides list of initial languages for multi-language search.
   */
  @NotEmpty
  @Size(max = 5)
  private Set<String> initialLanguages = emptySet();

  /**
   * Provides map with global features configuration. Can be overwritten by tenant configuration.
   */
  private Map<TenantConfiguredFeature, Boolean> searchFeatures = emptyMap();
}
