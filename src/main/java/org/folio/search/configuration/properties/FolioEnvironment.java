package org.folio.search.configuration.properties;

import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.firstNonBlank;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class FolioEnvironment {

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
