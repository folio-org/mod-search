package org.folio.search.utils;

import static org.folio.search.utils.TestConstants.ENV;
import static org.folio.search.utils.TestUtils.removeEnvProperty;
import static org.folio.search.utils.TestUtils.setEnvProperty;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public abstract class EnvironmentUnitTest {

  @BeforeAll
  static void beforeAll() {
    setEnvProperty(ENV);
  }

  @AfterAll
  static void afterAll() {
    removeEnvProperty();
  }
}
