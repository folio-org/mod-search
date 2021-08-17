package org.folio.search.configuration.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.configuration.properties.FolioEnvironment.getFolioEnvName;
import static org.folio.search.utils.TestUtils.removeEnvProperty;
import static org.folio.search.utils.TestUtils.setEnvProperty;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
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
    assertThat(getFolioEnvName()).isNull();
  }

  @Test
  void shouldReturnDefaultFolioEnvIfPropertyIsEmpty() {
    setEnvProperty("   ");
    assertThat(getFolioEnvName()).isNull();
  }

  @ValueSource(strings = {"a", "Z", "0", "9", "_", "-"})
  @ParameterizedTest
  void shouldNotThrowExceptionWhenEnvHasAllowedChars(String env) {
    setEnvProperty(env);
    assertThat(getFolioEnvName()).isEqualTo(env);
  }

  @ParameterizedTest
  @ValueSource(strings = {"!", "@", "%$$#", "def qa"})
  void shouldThrowExceptionWhenEnvHasDisallowedChars(String env) {
    var validator = Validation.buildDefaultValidatorFactory().getValidator();
    var folioEnvironment = FolioEnvironment.of(env);
    var validationResponse = validator.validate(folioEnvironment);
    assertThat(validationResponse).isNotEmpty()
      .map(ConstraintViolation::getMessage)
      .containsExactly("Value must follow the pattern: '[\\w0-9\\-_]+'");
  }
}
