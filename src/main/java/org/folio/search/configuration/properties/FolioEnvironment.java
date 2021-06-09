package org.folio.search.configuration.properties;

import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static org.apache.commons.lang3.StringUtils.firstNonBlank;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class FolioEnvironment {
  private final String okapiUrl;
  private final String env;

  public FolioEnvironment(@Value("${okapi.url}") String okapiUrl) {
    this.okapiUrl = okapiUrl;
    this.env = getFolioEnvName();
  }

  public static String getFolioEnvName() {
    return validateFolioEnv(firstNonBlank(getenv("ENV"), getProperty("env"), "folio"));
  }

  private static String validateFolioEnv(String folioEnv) {
    if (!folioEnv.matches("[\\w0-9\\-_]+")) {
      throw new IllegalArgumentException("Folio ENV must contain alphanumeric and '-', '_' symbols only");
    }

    return folioEnv;
  }
}
