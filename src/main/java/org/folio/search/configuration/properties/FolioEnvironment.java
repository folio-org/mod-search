package org.folio.search.configuration.properties;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class FolioEnvironment {
  private final String okapiUrl;

  public FolioEnvironment(@Value("${okapi.url}") String okapiUrl) {
    this.okapiUrl = okapiUrl;
  }
}
