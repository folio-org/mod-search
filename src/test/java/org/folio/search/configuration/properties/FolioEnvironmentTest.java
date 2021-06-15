package org.folio.search.configuration.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.configuration.properties.FolioEnvironment.getFolioEnvName;
import static org.folio.search.utils.TestUtils.removeEnvProperty;
import static org.folio.search.utils.TestUtils.setEnvProperty;

import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class FolioEnvironmentTest {
  @AfterEach
  void resetEnvPropertyValue() {
    removeEnvProperty();
  }

  @Test
  void shouldReturnFolioEnvFromProperties() {
    setEnvProperty("test-env");

    assertThat(getFolioEnvName()).isEqualTo("test-env");
  }

  @Test
  void shouldReturnDefaultFolioEnvIfPropertyNotSet() {
    assertThat(getFolioEnvName()).isEqualTo("folio");
  }

  @Test
  void shouldReturnDefaultFolioEnvIfPropertyIsEmpty() {
    setEnvProperty("   ");
    assertThat(getFolioEnvName()).isEqualTo("folio");
  }

  @ValueSource(strings = {"a", "Z", "0", "9", "_", "-"})
  @ParameterizedTest
  void shouldNotThrowExceptionWhenEnvHasAllowedChars(String env) {
    setEnvProperty(env);
    assertThat(getFolioEnvName()).isEqualTo(env);
  }

  @ValueSource(strings = {"!", "@", "%$$#", "def qa"})
  @ParameterizedTest
  void shouldThrowExceptionWhenEnvHasDisallowedChars(String env) {
    setEnvProperty(env);

    assertThatThrownBy(FolioEnvironment::getFolioEnvName)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Folio ENV must contain alphanumeric and '-', '_' symbols only");
  }
}
