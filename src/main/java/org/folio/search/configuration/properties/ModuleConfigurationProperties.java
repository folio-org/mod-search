package org.folio.search.configuration.properties;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class ModuleConfigurationProperties {

  private final String okapiUrl;
  private final String moduleName;

  /**
   * Used by dependency injection framework to extract system configuration.
   *
   * @param okapiUrl - okapi URL as {@link String} object
   * @param moduleName - application name as {@link String} object
   */
  public ModuleConfigurationProperties(
    @Value("${okapi.url}") String okapiUrl,
    @Value("${spring.application.name}") String moduleName) {
    this.okapiUrl = okapiUrl;
    this.moduleName = moduleName;
  }
}
