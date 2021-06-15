package org.folio.search.configuration.properties;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class OkapiConfigurationProperties {
  private final String okapiUrl;

  public OkapiConfigurationProperties(@Value("${okapi.url}") String okapiUrl) {
    this.okapiUrl = okapiUrl;
  }
}
