package org.folio.search.configuration.properties;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import lombok.Data;
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
   * Contains set of ignored search processor names for resource.
   */
  private Map<String, Set<String>> disabledSearchOptions = emptyMap();
}
